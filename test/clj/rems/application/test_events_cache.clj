(ns ^:integration rems.application.test-events-cache
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.application.events-cache :as events-cache]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.db.users :as users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-refresh
  (let [user-id "user"
        _ (users/add-user! user-id {})
        workflow-id (:id (db/create-workflow! {:organization ""
                                               :owneruserid ""
                                               :modifieruserid ""
                                               :title ""
                                               :fnlround 1
                                               :workflow "{}"}))
        app-id (:id (db/create-application! {:user ""
                                             :wfid workflow-id
                                             :start (time/now)}))
        cache (events-cache/new)]

    (testing "1st refresh, no events"
      (is (= nil (events-cache/refresh! cache (fn [_state _events]
                                                (assert false "should not be called"))))))

    (testing "2nd refresh, new events"
      (let [expected-event {:event/type :application.event/submitted
                            :event/time (time/now)
                            :event/actor user-id
                            :application/id app-id}]
        (applications/add-dynamic-event! expected-event)
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
      (let [expected-event {:event/type :application.event/approved
                            :event/time (time/now)
                            :event/actor user-id
                            :application/id app-id
                            :application/comment ""}]
        (applications/add-dynamic-event! expected-event)
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
               ;; in case the cache uses mount and somebody forgets to start it
               (events-cache/refresh! (mount/->NotStartedState "foo") update-fn)))))))
