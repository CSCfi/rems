(ns ^:integration rems.db.test-user-settings
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.user-settings]
            [rems.db.users]
            [schema.core :as s]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-user-settings
  (rems.db.users/add-user! "user" {})
  (rems.db.users/add-user! "unrelated" {})

  (testing "default settings for a new user"
    (is (= {:language :en
            :notification-email nil}
           (s/validate rems.db.user-settings/UserSettings
                       (rems.db.user-settings/get-user-settings "user")))))

  (testing "add settings"
    (is (= {:success true}
           (rems.db.user-settings/update-user-settings! "user" {:language :fi})))
    (is (= {:language :fi
            :notification-email nil}
           (rems.db.user-settings/get-user-settings "user"))))

  (testing "modify settings"
    (is (= {:success true}
           (rems.db.user-settings/update-user-settings! "user" {:notification-email "user@example.com"})))
    (is (= {:language :fi
            :notification-email "user@example.com"}
           (rems.db.user-settings/get-user-settings "user"))))

  (testing "updating with empty settings does not change settings"
    (is (= {:success true}
           (rems.db.user-settings/update-user-settings! "user" {})))
    (is (= {:language :fi
            :notification-email "user@example.com"}
           (rems.db.user-settings/get-user-settings "user"))))

  (testing "updating with invalid settings does not change settings"
    (is (= {:success false}
           (rems.db.user-settings/update-user-settings! "user" {:notification-email "foo"}))
        "completely invalid")
    (is (= {:success false}
           (rems.db.user-settings/update-user-settings! "user" {:notification-email "bar@example.com" ; valid
                                                                :language :de})) ; invalid
        "partially invalid")
    (is (= {:language :fi
            :notification-email "user@example.com"}
           (rems.db.user-settings/get-user-settings "user"))))

  (testing "changing one user's settings does not change another user's settings"
    (is (= {:success true}
           (rems.db.user-settings/update-user-settings! "unrelated" {:notification-email "unrelated@example.com"})))
    (is (= {:success true}
           (rems.db.user-settings/update-user-settings! "user" {:notification-email "changed@example.com"})))
    (is (= {:language :en
            :notification-email "unrelated@example.com"}
           (rems.db.user-settings/get-user-settings "unrelated")))))

(deftest test-language
  (testing "valid language"
    (is (= {:language :en} (rems.db.user-settings/validate-new-settings {:language :en})))
    (is (= {:language :fi} (rems.db.user-settings/validate-new-settings {:language :fi}))))

  (testing "undefined language"
    (is (= nil (rems.db.user-settings/validate-new-settings {:language :de}))))

  (testing "nil language"
    (is (= nil (rems.db.user-settings/validate-new-settings {:language nil})))))

(deftest test-validate-new-settings
  (testing "valid email"
    (is (= {:notification-email "somebody@example.com"}
           (rems.db.user-settings/validate-new-settings {:notification-email "somebody@example.com"}))))

  (testing "invalid email"
    (is (= nil
           (rems.db.user-settings/validate-new-settings {:notification-email "somebody"}))))

  (testing "empty email will clear the setting"
    (is (= {:notification-email nil}
           (rems.db.user-settings/validate-new-settings {:notification-email ""}))))

  (testing "nil email will clear the setting"
    (is (= {:notification-email nil}
           (rems.db.user-settings/validate-new-settings {:notification-email nil})))))
