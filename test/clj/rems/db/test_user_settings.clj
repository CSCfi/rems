(ns ^:integration rems.db.test-user-settings
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-user-settings
  (users/add-user! "user" {:eppn "user"})
  (users/add-user! "unrelated" {:eppn "unrelated"})

  (testing "default settings for a new user"
    (is (= (user-settings/get-user-settings "user")
           {:language :en})))

  ;; TODO: use some other setting which has more than two possible values
  (testing "add settings"
    (user-settings/update-user-settings! "user" {:language :fi})
    (is (= (user-settings/get-user-settings "user")
           {:language :fi})))

  (testing "modify settings"
    (user-settings/update-user-settings! "user" {:language :en})
    (is (= (user-settings/get-user-settings "user")
           {:language :en})))

  (testing "updating with empty settings does not change settings"
    (user-settings/update-user-settings! "user" {})
    (is (= (user-settings/get-user-settings "user")
           {:language :en})))

  (testing "changing one user's settings does not change unrelated user's settings"
    (user-settings/update-user-settings! "unrelated" {:language :fi})
    (user-settings/update-user-settings! "user" {:language :en})
    (is (= (user-settings/get-user-settings "unrelated")
           {:language :fi}))))

(deftest test-user-language
  (testing "valid language"
    (is (= {:language :en} (user-settings/validate-settings {:language :en})))
    (is (= {:language :fi} (user-settings/validate-settings {:language :fi}))))

  (testing "undefined language"
    (is (= {} (user-settings/validate-settings {:language :de}))))

  (testing "nil language"
    (is (= {} (user-settings/validate-settings {:language nil})))))
