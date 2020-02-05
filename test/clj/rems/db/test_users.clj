(ns ^:integration rems.db.test-users
  (:require [clojure.test :refer :all]
            [rems.db.roles :as roles]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.users :as users]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-users
  ;; TODO: enforce that userid and eppn must be same?
  (users/add-user! "user1" {:eppn "whatever"
                            :commonName "What Ever"
                            :some-attr "some value"})
  (users/add-user! "user-with-org" {:eppn "user-with-org"
                                    :commonName "User Org"
                                    :mail "user@org"
                                    :organization "org"})

  (testing "get-raw-user-attributes"
    (is (= {:eppn "whatever"
            :commonName "What Ever"
            :some-attr "some value"}
           (#'users/get-raw-user-attributes "user1")))
    (is (= {:eppn "user-with-org"
            :commonName "User Org"
            :mail "user@org"
            :organization "org"}
           (#'users/get-raw-user-attributes "user-with-org"))))

  (testing "get-user"
    (is (= {:userid "user1"
            :name "What Ever"
            :email nil}
           (users/get-user "user1")))
    (is (= {:userid "user-with-org"
            :name "User Org"
            :email "user@org"
            :organization "org"}
           (users/get-user "user-with-org"))))

  (testing "get-all-users"
    (is (= [{:eppn "user-with-org"
             :commonName "User Org"
             :mail "user@org"
             :organization "org"}
            {:eppn "whatever"
             :commonName "What Ever"
             :some-attr "some value"}]
           (sort-by :eppn (#'users/get-all-users)))))

  (testing "get-users-with-role"
    (roles/add-role! "user1" :owner)
    (is (= ["user1"] (users/get-users-with-role :owner)))
    (is (= [] (users/get-users-with-role :reporter))))

  (testing "update user"
    (users/add-user! "user1" {:eppn "user1"
                              :commonName "new name"})
    (is (= {:userid "user1"
            :name "new name"
            :email nil}
           (users/get-user "user1")))))

(deftest test-nonexistent-user
  (is (= {:userid "nonexistent"
          :name nil
          :email nil}
         (users/get-user "nonexistent"))))
