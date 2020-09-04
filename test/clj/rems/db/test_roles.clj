(ns ^:integration rems.db.test-roles
  (:require [clojure.test :refer :all]
            [rems.db.roles :as roles]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.users :as users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-roles
  (testing "get-roles for unknown user"
    (is (= #{:logged-in} (roles/get-roles "unknown-user"))))
  (users/add-user! {:userid "user"})
  (testing "add role"
    (testing "as keyword"
      (roles/add-role! "user" :reporter)
      (is (= #{:logged-in :reporter} (roles/get-roles "user"))))
    (testing "is idempotent"
      (roles/add-role! "user" :owner)
      (is (= #{:logged-in :reporter :owner} (roles/get-roles "user")))))
  (testing "remove role"
    (roles/remove-role! "user" :owner)
    (is (= #{:logged-in :reporter} (roles/get-roles "user")))
    (testing "is idempotent"
      (roles/remove-role! "user" :owner)
      (is (= #{:logged-in :reporter} (roles/get-roles "user")))))
  (testing "can't add unknown role"
    (is (thrown? RuntimeException (roles/add-role! "bob" :unknown-role))))
  (testing "can't remove unknown role"
    (is (thrown? RuntimeException (roles/remove-role! "bob" :unknown-role)))))
