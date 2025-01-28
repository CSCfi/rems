(ns ^:integration rems.db.test-blacklist
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.blacklist]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-blacklist-event-storage
  (test-helpers/create-user! {:userid "user1"})
  (test-helpers/create-user! {:userid "user2"})
  (test-helpers/create-user! {:userid "handler"})
  (test-helpers/create-resource! {:resource-ext-id "urn.fi/123"})
  (test-helpers/create-resource! {:resource-ext-id "urn.fi/124"})

  (let [event-1 (rems.db.blacklist/add-event! {:event/type :blacklist.event/add
                                               :event/actor "handler"
                                               :event/time (time/date-time 2019 1 2 8 0 0)
                                               :userid "user1"
                                               :resource/ext-id "urn.fi/123"
                                               :event/comment "1"})
        event-2 (rems.db.blacklist/add-event! {:event/type :blacklist.event/remove
                                               :event/actor "handler"
                                               :event/time (time/date-time 2019 2 3 9 0 0)
                                               :userid "user1"
                                               :resource/ext-id "urn.fi/123"
                                               :event/comment "2"})
        event-3 (rems.db.blacklist/add-event! {:event/type :blacklist.event/add
                                               :event/actor "handler"
                                               :event/time (time/date-time 2019 1 1 1 0 0)
                                               :userid "user2"
                                               :resource/ext-id "urn.fi/124"
                                               :event/comment "3"})]
    (testing "cache reload works"
      (cache/set-uninitialized! rems.db.blacklist/blacklist-event-cache)
      (is (= {event-1 {:event/id event-1
                       :event/type :blacklist.event/add
                       :event/actor "handler"
                       :event/time (time/date-time 2019 1 2 8 0 0)
                       :userid "user1"
                       :resource/ext-id "urn.fi/123"
                       :event/comment "1"}
              event-2 {:event/id event-2
                       :event/type :blacklist.event/remove
                       :event/actor "handler"
                       :event/time (time/date-time 2019 2 3 9 0 0)
                       :userid "user1"
                       :resource/ext-id "urn.fi/123"
                       :event/comment "2"}
              event-3 {:event/id event-3
                       :event/type :blacklist.event/add
                       :event/actor "handler"
                       :event/time (time/date-time 2019 1 1 1 0 0)
                       :userid "user2"
                       :resource/ext-id "urn.fi/124"
                       :event/comment "3"}}
             (into {} (cache/entries! rems.db.blacklist/blacklist-event-cache))))))

  (let [events (rems.db.blacklist/get-events {:resource/ext-id "urn.fi/123"})]
    ;; event id sequence numbers aren't predictable since even
    ;; rollbacked transactions consume id sequences
    (is (distinct? (map :event/id events)))
    (is (= [{:event/type :blacklist.event/add
             :event/time (time/date-time 2019 1 2 8 0 0)
             :event/actor "handler"
             :userid "user1"
             :resource/ext-id "urn.fi/123"
             :event/comment "1"}
            {:event/type :blacklist.event/remove
             :event/time (time/date-time 2019 2 3 9 0 0)
             :event/actor "handler"
             :userid "user1"
             :resource/ext-id "urn.fi/123"
             :event/comment "2"}]
           (map #(dissoc % :event/id) events))))
  (is (= [{:event/type :blacklist.event/add
           :event/time (time/date-time 2019 1 1 1 0 0)
           :event/actor "handler"
           :userid "user2"
           :resource/ext-id "urn.fi/124"
           :event/comment "3"}]
         (mapv #(dissoc % :event/id) (rems.db.blacklist/get-events {:userid "user2"}))))
  (is (not (rems.db.blacklist/blacklisted? "user1" "urn.fi/123"))
      "user was added to blacklist, then removed")
  (is (not (rems.db.blacklist/blacklisted? "user1" "urn.fi/124"))
      "user was never added to blacklist")
  (is (not (rems.db.blacklist/blacklisted? "user2" "urn.fi/123"))
      "user was never added to blacklist")
  (is (rems.db.blacklist/blacklisted? "user2" "urn.fi/124")
      "user was added to blacklist but not removed")

  (testing "events are returned in correct order"
    (doseq [id (range 4 20)]
      (rems.db.blacklist/add-event! {:event/type (if (odd? id)
                                                   :blacklist.event/add
                                                   :blacklist.event/remove)
                                     :event/actor "handler"
                                     :event/time (time/date-time 2019 1 1 1 0 0)
                                     :userid "user2"
                                     :resource/ext-id "urn.fi/124"
                                     :event/comment (str id)}))
    (let [events (rems.db.blacklist/get-events)]
      (is (apply < (mapv :event/id events)))
      (is (= ["1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11" "12" "13" "14" "15" "16" "17" "18" "19"]
             (mapv :event/comment events))))))

(deftest test-parameter-validation
  (let [user-id "test-user"
        resource-ext-id "test-resource"
        command {:event/type :blacklist.event/add
                 :event/actor "handler"
                 :event/comment ""
                 :event/time (time/now)
                 :resource/ext-id resource-ext-id
                 :userid user-id}]
    (test-helpers/create-user! {:userid user-id})
    (test-helpers/create-resource! {:resource-ext-id resource-ext-id})

    (testing "user and resource both exist"
      (is (not (rems.db.blacklist/blacklisted? user-id resource-ext-id)))
      (rems.db.blacklist/add-event! command)
      (is (rems.db.blacklist/blacklisted? user-id resource-ext-id)))))
