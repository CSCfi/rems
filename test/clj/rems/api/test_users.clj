(ns ^:integration rems.api.test-users
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.api-key :as api-key]
            [rems.db.roles :as roles]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each ;; active-api-test needs a fresh session store
  api-fixture-without-data
  owners-fixture)

(deftest users-api-test
  (let [new-user {:userid "david"
                  :email "d@av.id"
                  :name "David Newuser"}
        userid (:userid new-user)]
    (testing "create"
      (is (= nil (:name (users/get-user userid))))
      (-> (request :post (str "/api/users/create"))
          (json-body new-user)
          (authenticate +test-api-key+ "owner")
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
          (authenticate +test-api-key+ "owner")
          handler
          assert-response-is-ok)
      (is (= {:userid "david"
              :email "new email"
              :name "new name"} (users/get-user userid)))))

  (testing "create user with organization and nickname, without email"
    (let [userid "user-with-org"]
      (is (= nil (:name (users/get-user userid))))
      (-> (request :post (str "/api/users/create"))
          (json-body {:userid userid
                      :name "User Org"
                      :nickname "Orger"
                      :email nil
                      :organizations [{:organization/id "abc"}]})
          (authenticate +test-api-key+ "owner")
          handler
          assert-response-is-ok)
      (is (= {:userid userid
              :email nil
              :name "User Org"
              :nickname "Orger"
              :organizations [{:organization/id "abc"}]} (users/get-user userid))))))

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
                         (authenticate +test-api-key+ "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response))))))

  (testing "user with user-owner role"
    (users/add-user! {:userid "user-owner"})
    (roles/add-role! "user-owner" :user-owner)
    (-> (request :post (str "/api/users/create"))
        (json-body {:userid "test1"
                    :email "test1@example.com"
                    :name "Test 1"})
        (authenticate +test-api-key+ "user-owner")
        handler
        assert-response-is-ok)))

(deftest active-api-test
  (test-data/create-test-users-and-roles!)
  (testing "no users yet"
    (is (= []
           (api-call :get "/api/users/active" nil
                     +test-api-key+ "owner"))))
  (testing "log in elsa"
    (let [cookie (login-with-cookies "elsa")]
      (-> (request :get "/api/keepalive")
          (header "Cookie" cookie)
          handler
          assert-response-is-ok)
      (is (= [{:userid "elsa" :name "Elsa Roleless" :email "elsa@example.com"}]
             (api-call :get "/api/users/active" nil
                       +test-api-key+ "owner")))))
  (testing "log in frank"
    (let [cookie (login-with-cookies "frank")]
      (-> (request :get "/api/keepalive")
          (header "Cookie" cookie)
          handler
          assert-response-is-ok)
      (is (= #{{:userid "elsa" :name "Elsa Roleless" :email "elsa@example.com"}
               {:userid "frank" :name "Frank Roleless" :email "frank@example.com" :organizations [{:organization/id "frank"}]}}
             (set (api-call :get "/api/users/active" nil
                            +test-api-key+ "owner")))))))
