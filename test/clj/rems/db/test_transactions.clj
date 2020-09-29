(ns ^:integration rems.db.test-transactions
  (:require [clojure.test :refer :all]
            [conman.core :as conman]
            [rems.application.commands :as commands]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture reset-db-fixture]]
            [rems.db.users :as users])
  (:import [java.sql SQLException]
           [java.util.concurrent Executors Future TimeUnit ExecutorService]
           [org.postgresql.util PSQLException]))

(use-fixtures
  :once
  test-db-fixture
  reset-db-fixture)

(defn- create-dummy-user []
  (let [user-id "user"]
    (users/add-user! {:userid user-id})
    user-id))

(defn- transaction-conflict? [^Exception e]
  (cond
    (nil? e) false
    (instance? PSQLException e) (.contains (.getMessage e)
                                           "The transaction might succeed if retried")
    :else (transaction-conflict? (.getCause e))))

(defn- sample-until-interrupted [f]
  (loop [results []]
    (if (.isInterrupted (Thread/currentThread))
      results
      (recur (try
               (conj results (f))
               (catch InterruptedException _
                 (.interrupt (Thread/currentThread))
                 results)
               ;; XXX: HikariPool.getConnection wraps InterruptedException into SQLException
               (catch SQLException e
                 (if (instance? InterruptedException (.getCause e))
                   (do (.interrupt (Thread/currentThread))
                       results)
                   (throw e))))))))

(defn- submit-all [^ExecutorService thread-pool tasks]
  (doall (for [task tasks]
           (.submit thread-pool ^Callable task))))

(deftest test-event-publishing-consistency
  (let [test-duration-millis 2000
        applications-count 5
        writers-per-application 5
        concurrent-readers 5
        user-id (create-dummy-user)
        app-ids (vec (for [_ (range applications-count)]
                       (test-helpers/create-application! {:actor user-id})))
        ;; Currently we only test that commands are not executed concurrently
        ;; for a single application. To guarantee that, we could add an app version
        ;; column to the events table with constraint `UNIQUE (appId, appVersion)`.
        observed-app-version-marker "999"
        old-handle-command commands/handle-command
        mark-observed-app-version (fn [result application]
                                    (if (and (not (:errors result))
                                             (= :application.event/draft-saved (:event/type (first (:events result)))))
                                      (assoc-in result [:events 0 :application/field-values] [{:form 1 :field observed-app-version-marker :value (str (count (:application/events application)))}])
                                      result))
        wrapped-handle-command (fn [cmd application injections]
                                 (mark-observed-app-version (old-handle-command cmd application injections)
                                                            application))]
    (with-redefs [commands/handle-command wrapped-handle-command
                  rems.config/env (assoc rems.config/env
                                         :database-lock-timeout "4s"
                                         :database-idle-in-transaction-session-timeout "8s")]
      (let [write-event (fn [app-id]
                          (try
                            (conman/with-transaction [db/*db* {:isolation :serializable}]
                              (test-helpers/command!
                               {:type :application.command/save-draft
                                :application-id app-id
                                :actor user-id
                                :field-values []}))
                            (catch Exception e
                              (if (transaction-conflict? e)
                                ::transaction-conflict
                                (throw e)))))
            read-app-events (fn [app-id]
                              (conman/with-transaction [db/*db* {:isolation :serializable
                                                                 :read-only? true}]
                                {::app-id app-id
                                 ::events (events/get-application-events app-id)}))
            read-all-events (fn []
                              (conman/with-transaction [db/*db* {:isolation :serializable
                                                                 :read-only? true}]
                                {::events (events/get-all-events-since 0)}))
            thread-pool (Executors/newCachedThreadPool)
            app-events-readers (submit-all thread-pool (for [app-id app-ids]
                                                         (fn [] (sample-until-interrupted
                                                                 (fn [] (read-app-events app-id))))))
            all-events-readers (submit-all thread-pool (for [_ (range concurrent-readers)]
                                                         (fn [] (sample-until-interrupted
                                                                 (fn [] (read-all-events))))))
            writers (submit-all thread-pool (for [app-id app-ids
                                                  _ (range writers-per-application)]
                                              (fn [] (sample-until-interrupted
                                                      (fn [] (write-event app-id))))))]
        (Thread/sleep test-duration-millis)
        (doto thread-pool
          (.shutdownNow)
          (.awaitTermination 30 TimeUnit/SECONDS))

        (let [writer-attempts (flatten (map #(.get ^Future %) writers))
              writer-results (remove #{::transaction-conflict} writer-attempts)
              app-events-reader-results (flatten (map #(.get ^Future %) app-events-readers))
              all-events-reader-results (flatten (map #(.get ^Future %) all-events-readers))
              final-events (events/get-all-events-since 0)
              final-events-by-app-id (group-by :application/id final-events)]
          (comment
            (prn 'writer-attempts (count writer-attempts))
            (prn 'writer-results (count writer-results))
            (prn 'app-events-reader-results (count app-events-reader-results))
            (prn 'all-events-reader-results (count all-events-reader-results)))

          (testing "all commands succeeded"
            (is (seq writer-results)
                "at least one result")
            (is (every? #(= [:events] (keys %))
                        writer-results)
                "no errors")
            (is (= (count writer-results)
                   (count writer-attempts))
                "should have no transaction conflicts"))

          (testing "all events were written"
            (is (= (+ (count app-ids) ; one application created event per application
                      (count writer-results))
                   (count final-events))))

          (testing "event IDs are observed in monotonically increasing order, within one application"
            (doseq [observed app-events-reader-results]
              (let [final-events (get final-events-by-app-id (::app-id observed))]
                (is (= (->> final-events
                            (take (count (::events observed)))
                            (map :event/id))
                       (->> (::events observed)
                            (map :event/id)))))))

          (testing "event IDs are observed in monotonically increasing order, globally"
            (doseq [observed all-events-reader-results]
              (is (= (->> final-events
                          (take (count (::events observed)))
                          (map :event/id))
                     (->> (::events observed)
                          (map :event/id))))))

          (testing "commands are executed serially"
            (doseq [[id events] final-events-by-app-id]
              (testing id
                (is (= (->> (range 1 (count events))
                            (map str))
                       (->> events
                            (filter #(= :application.event/draft-saved (:event/type %)))
                            (map #(get-in % [:application/field-values 0 :value]))))))))

          (testing "there are not gaps in event IDs"
            ;; There might still be gaps in the IDs if the transaction is rolled back
            ;; for an unrelated reason after the command is executed. To guarantee no gaps,
            ;; the event IDs could be generated with `max(id) + 1`.
            (is (every? (fn [[a b]] (= (inc a) b))
                        (->> (map :event/id final-events)
                             (partition 2 1))))))))))
