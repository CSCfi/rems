(ns ^:integration rems.db.test-transactions
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.resource :as resource]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture test-data-fixture]]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow])
  (:import [java.util UUID]
           [java.util.concurrent Executors Future TimeUnit]
           [org.postgresql.util PSQLException]))

(use-fixtures
  :once
  test-db-fixture)

(defn- create-dummy-user []
  (let [user-id "user"]
    (users/add-user! user-id {:eppn user-id})
    user-id))

(defn- create-dummy-application [user-id]
  (let [workflow-id (:id (workflow/create-workflow! {:user-id user-id
                                                     :organization ""
                                                     :title ""
                                                     :type :dynamic
                                                     :handlers []}))
        form-id (:id (form/create-form! user-id
                                        {:organization ""
                                         :title ""
                                         :fields []}))
        res-id (:id (resource/create-resource! {:resid (str "urn:uuid:" (UUID/randomUUID))
                                                :organization ""
                                                :licenses []}
                                               user-id))
        cat-id (:id (catalogue/create-catalogue-item! {:title ""
                                                       :form form-id
                                                       :resid res-id
                                                       :wfid workflow-id}))
        app-id (:application-id (applications/create-application! user-id [cat-id]))]
    (assert app-id)
    app-id))

(defn- transaction-conflict? [^Exception e]
  (cond
    (nil? e) false
    (instance? PSQLException e) (.contains (.getMessage e)
                                           "The transaction might succeed if retried")
    :else (transaction-conflict? (.getCause e))))

(defn- retry-transaction [f]
  (while (and (not (.isInterrupted (Thread/currentThread)))
              (not (try
                     (f)
                     true
                     (catch Exception e
                       (if (transaction-conflict? e)
                         false
                         (throw e))))))))

(deftest test-event-publishing-consistency
  (let [written-events-count 50
        concurrent-writers-count 10
        timeout-seconds 30
        user-id (create-dummy-user)
        app-ids (vec (for [_ (range concurrent-writers-count)]
                       (create-dummy-application user-id)))
        writers-pool (Executors/newFixedThreadPool concurrent-writers-count)
        readers-pool (Executors/newCachedThreadPool)
        write-event (fn []
                      (retry-transaction
                       (fn []
                         ;; Note: currently these tests pass only with serializable isolation
                         (conman/with-transaction [db/*db* {:isolation :serializable}]
                           (applications/command!
                            {:type :application.command/save-draft
                             :time (time/now)
                             :actor user-id
                             :application-id (rand-nth app-ids)
                             :field-values []})))))
        read-app-events (fn [app-id]
                          (conman/with-transaction [db/*db* {:isolation :read-committed
                                                             :read-only? true}]
                            (applications/get-application-events app-id)))
        read-all-events (fn []
                          (conman/with-transaction [db/*db* {:isolation :read-committed
                                                             :read-only? true}]
                            (applications/get-all-events-since 0)))
        read-app-events-repeatedly (fn [app-id]
                                     (let [results (atom [])]
                                       (while (not (.isTerminated writers-pool))
                                         (swap! results conj {::app-id app-id
                                                              ::events (read-app-events app-id)}))
                                       @results))
        read-all-events-repeatedly (fn []
                                     (let [results (atom [])]
                                       (while (not (.isTerminated writers-pool))
                                         (swap! results conj {::events (read-all-events)}))
                                       @results))
        app-readers (doall (for [app-id app-ids]
                             (.submit readers-pool ^Callable (fn [] (read-app-events-repeatedly app-id)))))
        all-readers (doall (for [_ (range concurrent-writers-count)]
                             (.submit readers-pool ^Callable read-all-events-repeatedly)))
        writers (doall (for [_ (range written-events-count)]
                         (.submit writers-pool ^Callable write-event)))]
    (.shutdown writers-pool)
    (.awaitTermination writers-pool timeout-seconds TimeUnit/SECONDS)
    (.shutdown readers-pool)
    (.awaitTermination readers-pool timeout-seconds TimeUnit/SECONDS)

    (prn 'app-ids app-ids)
    (let [writer-results (map #(.get ^Future %) writers)
          app-reader-results (flatten (map #(.get ^Future %) app-readers))
          all-reader-results (flatten (map #(.get ^Future %) all-readers))
          final-events (read-all-events)
          final-events-by-app-id (into {} (for [app-id app-ids]
                                            [app-id (read-app-events app-id)]))]
      (prn 'writer-results (count writer-results))
      (prn 'app-reader-results (count app-reader-results))
      (prn 'all-reader-results (count all-reader-results))

      (testing "all commands succeeded"
        (is (= written-events-count (count writer-results)))
        (is (every? nil? writer-results)))

      (testing "all events were written"
        (is (= (+ (count app-ids) ; number of created events
                  written-events-count)
               (count (applications/get-all-events-since 0)))))

      (testing "event IDs are observed in monotonically increasing order, within one application"
        (doseq [observed app-reader-results]
          (let [final-events (get final-events-by-app-id (::app-id observed))]
            (is (= (->> final-events
                        (take (count (::events observed)))
                        (map :event/id))
                   (->> (::events observed)
                        (map :event/id)))))))

      (testing "event IDs are observed in monotonically increasing order, globally"
        (doseq [observed all-reader-results]
          (is (= (->> final-events
                      (take (count (::events observed)))
                      (map :event/id))
                 (->> (::events observed)
                      (map :event/id)))))

        ;; TODO?
        #_(testing "there are not gaps in event IDs"
            (is (every? (fn [[a b]] (= (inc a) b))
                        (->> (map :event/id final-events)
                             (partition 2 1)))))))))
