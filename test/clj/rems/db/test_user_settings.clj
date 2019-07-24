(ns ^:integration rems.db.test-user-settings
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-user-settings
  (users/add-user! "user1" {:eppn "user1"})
  (testing "user settings are empty for a new user"
    (is (= (user-settings/get-user-settings "user1")
           {})))

  (testing "add a user setting"
    (user-settings/update-user-settings! "user1" {:language :en})
    (is (= (user-settings/get-user-settings "user1")
           {:language :en})))

  (testing "modify user setting"
    (user-settings/update-user-settings! "user1" {:language :fi})
    (is (= (user-settings/get-user-settings "user1")
           {:language :fi}))))
