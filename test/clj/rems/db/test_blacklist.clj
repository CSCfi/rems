(ns ^:integration rems.db.test-blacklist
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.blacklist :as blacklist]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures
  :each
  test-db-fixture
  rollback-db-fixture)

(deftest test-blacklist-event-storage
  (test-helpers/create-user! {:userid "user1"})
  (test-helpers/create-user! {:userid "user2"})
  (test-helpers/create-user! {:userid "handler"})
  (test-helpers/create-resource! {:resource-ext-id "urn.fi/123"})
  (test-helpers/create-resource! {:rseource-ext-id "urn.fi/124"})

  (blacklist/add-event! {:event/type :blacklist.event/add
                         :event/actor "handler"
                         :event/time (time/date-time 2019 1 2 8 0 0)
                         :userid "user1"
                         :resource/ext-id "urn.fi/123"
                         :event/comment nil})
  (blacklist/add-event! {:event/type :blacklist.event/remove
                         :event/actor "handler"
                         :event/time (time/date-time 2019 2 3 9 0 0)
                         :userid "user1"
                         :resource/ext-id "urn.fi/123"
                         :event/comment "it was ok"})
  (blacklist/add-event! {:event/type :blacklist.event/add
                         :event/actor "handler"
                         :event/time (time/date-time 2019 1 1 1 0 0)
                         :userid "user2"
                         :resource/ext-id "urn.fi/124"
                         :event/comment nil})

  (let [events (blacklist/get-events {:resource/ext-id "urn.fi/123"})]
    ;; event id sequence numbers aren't predictable since even
    ;; rollbacked transactions consume id sequences
    (is (distinct? (map :event/id events)))
    (is (= [{:event/type :blacklist.event/add
             :event/time (time/date-time 2019 1 2 8 0 0)
             :event/actor "handler"
             :userid "user1"
             :resource/ext-id "urn.fi/123"
             :event/comment nil}
            {:event/type :blacklist.event/remove
             :event/time (time/date-time 2019 2 3 9 0 0)
             :event/actor "handler"
             :userid "user1"
             :resource/ext-id "urn.fi/123"
             :event/comment "it was ok"}]
           (map #(dissoc % :event/id) events))))
  (is (= [{:event/type :blacklist.event/add
           :event/time (time/date-time 2019 1 1 1 0 0)
           :event/actor "handler"
           :userid "user2"
           :resource/ext-id "urn.fi/124"
           :event/comment nil}]
         (mapv #(dissoc % :event/id) (blacklist/get-events {:userid "user2"}))))
  (is (not (blacklist/blacklisted? "user1" "urn.fi/123"))
      "user was added to blacklist, then removed")
  (is (not (blacklist/blacklisted? "user1" "urn.fi/124"))
      "user was never added to blacklist")
  (is (not (blacklist/blacklisted? "user2" "urn.fi/123"))
      "user was never added to blacklist")
  (is (blacklist/blacklisted? "user2" "urn.fi/124")
      "user was added to blacklist but not removed"))
