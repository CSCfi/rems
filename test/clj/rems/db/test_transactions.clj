(ns ^:integration rems.db.test-transactions
  (:require [clojure.test :refer :all]
            [conman.core :as conman]
            [rems.config]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [reset-caches-fixture test-db-fixture reset-db-fixture]]
            [rems.db.users :as users])
  (:import [java.sql SQLException]
           [java.util.concurrent Executors Future TimeUnit ExecutorService]
           [org.postgresql.util PSQLException]))

(use-fixtures
  :once
  test-db-fixture
  reset-db-fixture
  reset-caches-fixture)

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

(defn- sample-until-finished [f coll]
  (loop [results []
         coll coll]
    (if (or (.isInterrupted (Thread/currentThread))
            (empty? coll))
      results
      (recur (try
               (conj results (f (first coll)))
               (catch InterruptedException _
                 (.interrupt (Thread/currentThread))
                 results)
               ;; XXX: HikariPool.getConnection wraps InterruptedException into SQLException
               (catch SQLException e
                 (if (instance? InterruptedException (.getCause e))
                   (do (.interrupt (Thread/currentThread))
                       results)
                   (throw e))))
             (rest coll)))))

(defn- submit-all [^ExecutorService thread-pool tasks]
  (doall (for [task tasks]
           (.submit thread-pool ^Callable task))))

(deftest test-event-publishing-consistency
  (let [test-duration-millis 2000
        applications-count 5
        writers-per-application 5
        concurrent-readers 5
        writes-per-application 5
        user-id (create-dummy-user)
        form-id (test-helpers/create-form! {:form/fields [{:field/id "fld1"
                                                           :field/title {:en "F" :fi "F" :sv "F"}
                                                           :field/type :text
                                                           :field/optional false}]})
        cat-id (test-helpers/create-catalogue-item! {:form-id form-id})
        app-ids (vec (for [_ (range applications-count)]
                       (test-helpers/create-application! {:actor user-id :catalogue-item-ids [cat-id]})))]

    ;; Currently we only test that commands are not executed concurrently
    ;; for a single application. To guarantee that, we could add an app version
    ;; column to the events table with constraint `UNIQUE (appId, appVersion)`.

    (with-redefs [rems.config/env (assoc rems.config/env
                                         :enable-save-compaction true
                                         :database-lock-timeout "4s"
                                         :database-idle-in-transaction-session-timeout "8s")]
      (let [write-event (fn [app-id x]
                          (try
                            (conman/with-transaction [db/*db* {:isolation :serializable}]
                              (test-helpers/command!
                               {:type :application.command/save-draft
                                :application-id app-id
                                :actor user-id
                                :field-values [{:form form-id :field "fld1" :value (str x)}]}))
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
                                              (fn [] (sample-until-finished
                                                      (fn [x] (write-event app-id x))
                                                      (range 1 (inc writes-per-application))))))]
        ;; TODO: we could finish right after all the writers have finished
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
            ;; count of events is two per application:
            ;; - one application created event per application
            ;; - one saved event per application (after compaction)
            (is (= (* 2 (count app-ids))
                   (count final-events))))

          (testing "event IDs are observed in monotonically increasing order, within one application"
            (doseq [observed app-events-reader-results
                    :let [events (mapv :event/id (::events observed))]]
              (is (apply < events))))

          (testing "event IDs are observed in monotonically increasing order, globally"
            (doseq [observed all-events-reader-results
                    :let [events (mapv :event/id (::events observed))]]
              (is (apply < events))))

          (testing "commands are executed serially"
            (doseq [[id events] final-events-by-app-id]
              (testing id
                (is (= (str writes-per-application) ; the last save should have this value
                       (->> events
                            (filter #(= :application.event/draft-saved (:event/type %)))
                            (map #(get-in % [:application/field-values 0 :value]))
                            first)))))))))))
