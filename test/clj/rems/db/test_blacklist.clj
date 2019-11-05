(ns ^:integration rems.db.test-blacklist
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.blacklist :as blacklist]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures
  :once
  test-db-fixture
  rollback-db-fixture)

(deftest test-blacklist-event-storage
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
  (is (= [{:event/id 1
           :event/type :blacklist.event/add
           :event/time (time/date-time 2019 1 2 8 0 0)
           :event/actor "handler"
           :userid "user1"
           :resource/ext-id "urn.fi/123"
           :event/comment nil}
          {:event/id 2
           :event/type :blacklist.event/remove
           :event/time (time/date-time 2019 2 3 9 0 0)
           :event/actor "handler"
           :userid "user1"
           :resource/ext-id "urn.fi/123"
           :event/comment "it was ok"}]
         (blacklist/get-events {:resource/ext-id "urn.fi/123"})))
  (is (= [{:event/id 3
           :event/type :blacklist.event/add
           :event/time (time/date-time 2019 01 01 01)
           :event/actor "handler"
           :userid "user2"
           :resource/ext-id "urn.fi/124"
           :event/comment nil}]
         (blacklist/get-events {:userid "user2"})))
  (is (not (blacklist/blacklisted? "user1" "urn.fi/123"))
      "user was added to blacklist, then removed")
  (is (not (blacklist/blacklisted? "user1" "urn.fi/124"))
      "user was never added to blacklist")
  (is (not (blacklist/blacklisted? "user2" "urn.fi/123"))
      "user was never added to blacklist")
  (is (blacklist/blacklisted? "user2" "urn.fi/124")
      "user was added to blacklist but not removed"))
