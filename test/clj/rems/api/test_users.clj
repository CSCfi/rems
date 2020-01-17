(ns ^:integration rems.api.test-users
  (:require [clojure.test :refer :all]
            [rems.db.api-key :as api-key]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest users-api-test
  (let [new-user {:userid "david"
                  :email "d@av.id"
                  :name "David Newuser"}
        userid (:userid new-user)]
    (testing "create"
      (is (= nil (:name (users/get-user userid))))
      (-> (request :post (str "/api/users/create"))
          (json-body new-user)
          (authenticate "42" "owner")
          handler
          assert-response-is-ok)
      (is (= {:userid "david"
              :email "d@av.id"
              :name "David Newuser"} (users/get-user userid))))

    (testing "update (or, create is idempotent)"
      (-> (request :post (str "/api/users/create"))
          (json-body (assoc new-user
                            :email "new email"
                            :name "new name"))
          (authenticate "42" "owner")
          handler
          assert-response-is-ok)
      (is (= {:userid "david"
              :email "new email"
              :name "new name"} (users/get-user userid))))))

(deftest users-api-security-test
  (testing "without authentication"
    (testing "create"
      (let [response (-> (request :post (str "/api/users/create"))
                         (json-body {:userid "test1"
                                     :email "test1@example.com"
                                     :name "Test 1"})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "create"
      (let [response (-> (request :post (str "/api/users/create"))
                         (json-body {:userid "test1"
                                     :email "test1@example.com"
                                     :name "Test 1"})
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response))))))

  (testing "user with user-owner role"
    (users/add-user! "user-owner" {:eppn "user-owner"})
    (roles/add-role! "user-owner" :user-owner)
    (testing "with api key with all roles"
      (-> (request :post (str "/api/users/create"))
          (json-body {:userid "test1"
                      :email "test1@example.com"
                      :name "Test 1"})
          (authenticate "42" "user-owner")
          handler
          assert-response-is-ok))
    (testing "with api key with only user-owner role"
      (api-key/add-api-key! "999" "" [:user-owner])
      (-> (request :post (str "/api/users/create"))
          (json-body {:userid "test2"
                      :email "test2@example.com"
                      :name "Test 2"})
          (authenticate "42" "user-owner")
          handler
          assert-response-is-ok))))
