(ns ^:integration rems.db.test-audit-log
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.db.core :as db]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-update-audit-log
  (let [row {:time (time/date-time 2025 1 2 8 0 0)
             :path "/api/user-settings"
             :method "get",
             :apikey "41",
             :userid "audit-log-user1",
             :status "200"}
        new-userid "audit-log-user2"
        num-rows-added (db/add-to-audit-log! row)
        added-row (first (db/get-audit-log {:userid (:userid row)}))
        num-rows-updated (db/update-audit-log! (assoc added-row :userid new-userid))
        updated-row (first (db/get-audit-log {:id (:id added-row)}))]

    (testing "update audit log"
      (is (= 1 (first num-rows-added)) "added once")
      (is (= 1 (first num-rows-updated)) "updated once")
      (is (= new-userid (:userid updated-row)) "correct update"))))
