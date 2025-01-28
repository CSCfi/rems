(ns ^:integration rems.db.test-roles
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.roles]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-roles
  (testing "can't add unknown role"
    (is (thrown? RuntimeException (rems.db.roles/add-role! "bob" :unknown-role))))

  (testing "can't remove unknown role"
    (is (thrown? RuntimeException (rems.db.roles/remove-role! "bob" :unknown-role))))

  (testing "default role is returned for unknown user"
    (is (= #{:logged-in} (rems.db.roles/get-roles "user"))))

  (testing "default role should not persist"
    (is (= #{} (rems.db.roles/get-users-with-role :logged-in))))

  (testing "create user and add owner role"
    (rems.db.users/add-user! "user" {:userid "user"})

    (is (= #{} (rems.db.roles/get-users-with-role :owner)))

    (rems.db.roles/add-role! "user" :owner)
    (is (= #{:logged-in :owner} (rems.db.roles/get-roles "user")))
    (is (= #{"user"} (rems.db.roles/get-users-with-role :owner))))

  (testing "add reporter role"
    (rems.db.roles/add-role! "user" :reporter)
    (is (= #{:logged-in :owner :reporter} (rems.db.roles/get-roles "user")))
    (is (= #{"user"} (rems.db.roles/get-users-with-role :owner)))
    (is (= #{"user"} (rems.db.roles/get-users-with-role :reporter))))

  (testing "add reporter role second time is idempotent"
    (rems.db.roles/add-role! "user" :reporter)
    (is (= #{:logged-in :owner :reporter} (rems.db.roles/get-roles "user")))
    (is (= #{"user"} (rems.db.roles/get-users-with-role :owner)))
    (is (= #{"user"} (rems.db.roles/get-users-with-role :reporter))))

  (testing "cache reload works"
    ;; force cache reload
    (cache/set-uninitialized! rems.db.roles/role-cache)
    (is (= {"user" #{:owner :reporter}}
           (into {} (cache/entries! rems.db.roles/role-cache))))

    (testing "dependent caches"
      (is (= {:owner #{"user"}
              :reporter #{"user"}}
             (into {} (cache/entries! @#'rems.db.roles/users-by-role))))))

  (testing "remove owner role"
    (rems.db.roles/remove-role! "user" :owner)
    (is (= #{:logged-in :reporter} (rems.db.roles/get-roles "user")))
    (is (= #{} (rems.db.roles/get-users-with-role :owner)))
    (is (= #{"user"} (rems.db.roles/get-users-with-role :reporter))))

  (testing "remove owner role second time is idempotent"
    (rems.db.roles/remove-role! "user" :owner)
    (is (= #{:logged-in :reporter} (rems.db.roles/get-roles "user")))
    (is (= #{} (rems.db.roles/get-users-with-role :owner)))
    (is (= #{"user"} (rems.db.roles/get-users-with-role :reporter)))))
