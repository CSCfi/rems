(ns ^:integration rems.application.test-events-cache
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.application.events-cache :as events-cache]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.db.users :as users])
  (:import [java.util UUID]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- create-dummy-user []
  (let [user-id (str (UUID/randomUUID))]
    (users/add-user! user-id {})
    user-id))

(defn- create-dummy-application []
  (let [workflow-id (:id (db/create-workflow! {:organization ""
                                               :owneruserid ""
                                               :modifieruserid ""
                                               :title ""
                                               :fnlround 1
                                               :workflow "{}"}))
        app-id (:id (db/create-application! {:user ""
                                             :wfid workflow-id
                                             :start (time/now)}))]
    app-id))

(defn- add-dummy-event! []
  (let [user-id (create-dummy-user)
        app-id (create-dummy-application)
        event {:event/type :application.event/submitted
               :event/time (time/now)
               :event/actor user-id
               :application/id app-id}]
    (events/add-event! event)
    event))

;; in case the cache uses mount and somebody forgets to start it
(def not-started-mount-state (mount/->NotStartedState "foo"))

(deftest test-refresh
  (let [cache (events-cache/new)]

    (testing "1st refresh, no events"
      (is (= nil (events-cache/refresh! cache (fn [_state _events]
                                                (assert false "should not be called"))))))

    (testing "2nd refresh, new events"
      (let [expected-event (add-dummy-event!)]
        (is (= "first result"
               (events-cache/refresh! cache
                                      (fn [state events]
                                        (is (nil? state))
                                        (is (= [expected-event]
                                               (->> events
                                                    (map #(dissoc % :event/id)))))
                                        "first result"))))))

    (testing "3rd refresh, no new events"
      (is (= "first result"
             (events-cache/refresh! cache (fn [_state _events]
                                            (assert false "should not be called"))))))

    (testing "4th refresh, new events"
      (let [expected-event (add-dummy-event!)]
        (is (= "second result"
               (events-cache/refresh! cache
                                      (fn [state events]
                                        (is (= "first result" state))
                                        (is (= [expected-event]
                                               (->> events
                                                    (map #(dissoc % :event/id)))))
                                        "second result"))))))

    (testing "cache disabled"
      (let [update-fn (fn [state events]
                        (is (nil? state))
                        (is (= 2 (count events)))
                        "non-cached result")]
        (is (= "non-cached result"
               (events-cache/refresh! nil update-fn)
               (events-cache/refresh! not-started-mount-state update-fn)))))))

(deftest test-empty
  (let [cache (events-cache/new)]
    (add-dummy-event!)
    (events-cache/refresh! cache (fn [_state _events] "foo"))
    (assert (<= 1 (:last-processed-event-id @cache)))
    (assert (= "foo" (:state @cache)))

    (testing "returns the cache to its original state"
      (events-cache/empty! cache)
      (is (= @(events-cache/new) @cache)))

    (testing "silently ignores disabled caches"
      (events-cache/empty! nil)
      (events-cache/empty! not-started-mount-state))))
