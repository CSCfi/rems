(ns ^:integration rems.db.test-user-settings
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]
            [schema.core :as s]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-user-settings
  (users/add-user! "user" {:eppn "user"})
  (users/add-user! "unrelated" {:eppn "unrelated"})

  (testing "default settings for a new user"
    (is (= {:language :en
            :email nil}
           (s/validate user-settings/UserSettings
                       (user-settings/get-user-settings "user")))))

  (testing "add settings"
    (user-settings/update-user-settings! "user" {:language :fi})
    (is (= {:language :fi
            :email nil}
           (user-settings/get-user-settings "user"))))

  (testing "modify settings"
    (user-settings/update-user-settings! "user" {:email "user@example.com"})
    (is (= {:language :fi
            :email "user@example.com"}
           (user-settings/get-user-settings "user"))))

  (testing "updating with empty settings does not change settings"
    (user-settings/update-user-settings! "user" {})
    (is (= {:language :fi
            :email "user@example.com"}
           (user-settings/get-user-settings "user"))))

  (testing "changing one user's settings does not change another user's settings"
    (user-settings/update-user-settings! "unrelated" {:email "unrelated@example.com"})
    (user-settings/update-user-settings! "user" {:email "changed@example.com"})
    (is (= {:language :en
            :email "unrelated@example.com"}
           (user-settings/get-user-settings "unrelated")))))

(deftest test-language
  (testing "valid language"
    (is (= {:language :en} (user-settings/validate-settings {:language :en})))
    (is (= {:language :fi} (user-settings/validate-settings {:language :fi}))))

  (testing "undefined language"
    (is (= {} (user-settings/validate-settings {:language :de}))))

  (testing "nil language"
    (is (= {} (user-settings/validate-settings {:language nil})))))

(deftest test-email
  (testing "valid email"
    (is (= {:email "somebody@example.com"} (user-settings/validate-settings {:email "somebody@example.com"}))))

  (testing "invalid email"
    (is (= {} (user-settings/validate-settings {:email "somebody"}))))

  (testing "empty email will clear the setting"
    (is (= {:email nil} (user-settings/validate-settings {:email ""}))))

  (testing "nil email will clear the setting"
    (is (= {:email nil} (user-settings/validate-settings {:email nil})))))
