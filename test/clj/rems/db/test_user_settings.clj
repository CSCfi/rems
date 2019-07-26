(ns ^:integration rems.db.test-user-settings
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-user-settings
  (users/add-user! "user1" {:eppn "user1"})
  (users/add-user! "user2" {:eppn "user2"})

  (testing "user settings have the default settings for a new user"
    (is (= (user-settings/get-user-settings "user1")
           {:language :en})))

  (testing "add a user setting"
    (user-settings/update-user-settings! "user1" {:language :en})
    (is (= (user-settings/get-user-settings "user1")
           {:language :en})))

  (testing "modify a user setting"
    (user-settings/update-user-settings! "user1" {:language :fi})
    (is (= (user-settings/get-user-settings "user1")
           {:language :fi})))

  (testing "updating with empty settings does not change the setting"
    (user-settings/update-user-settings! "user1" {})
    (is (= (user-settings/get-user-settings "user1")
           {:language :fi})))

  (testing "updating with nil language does not change the setting"
    (user-settings/update-user-settings! "user1" {:language nil})
    (is (= (user-settings/get-user-settings "user1")
           {:language :fi})))

  (testing "updating with undefined language does not change the setting"
    (user-settings/update-user-settings! "user1" {:language :de})
    (is (= (user-settings/get-user-settings "user1")
           {:language :fi})))

  (testing "modifying a setting does not change setting for an unrelated user"
    (is (= (user-settings/get-user-settings "user2")
           {:language :en}))))
