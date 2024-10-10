(ns ^:integration rems.api.test-users
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.testing-util :refer [with-fake-login-users]]
            [rems.db.roles]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]
            [rems.db.users]
            [rems.db.user-mappings]
            [rems.handler :refer [handler]]
            [rems.middleware :as middleware]
            [rems.service.test-data :as test-data]
            [rems.service.users]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each ;; test-active-api needs a fresh session store
  api-fixture
  owners-fixture)

(defn- assert-can-make-a-request! [cookie]
  (-> (request :get "/api/keepalive")
      (header "Cookie" cookie)
      handler
      assert-response-is-ok))

(deftest test-users-api
  (let [new-user {:userid "david"
                  :email "d@av.id"
                  :name "David Newuser"}
        userid (:userid new-user)]
    (testing "create"
      (is (= nil (:name (rems.db.users/get-user userid))))
      (-> (request :post (str "/api/users/create"))
          (json-body new-user)
          (authenticate +test-api-key+ "owner")
          handler
          assert-response-is-ok)
      (is (= {:userid "david"
              :email "d@av.id"
              :name "David Newuser"} (rems.db.users/get-user userid))))

    (testing "update with edit"
      (-> (request :put (str "/api/users/edit"))
          (json-body (assoc new-user
                            :email "new email"
                            :name "new name"))
          (authenticate +test-api-key+ "owner")
          handler
          assert-response-is-ok)
      (is (= {:userid "david"
              :email "new email"
              :name "new name"} (rems.db.users/get-user userid))))

    (testing "update with create (idempotent)"
      (-> (request :post (str "/api/users/create"))
          (json-body (assoc new-user
                            :email "new email2"
                            :name "new name2"))
          (authenticate +test-api-key+ "owner")
          handler
          assert-response-is-ok)
      (is (= {:userid "david"
              :email "new email2"
              :name "new name2"} (rems.db.users/get-user userid)))))

  (testing "create user with organization and nickname, without email"
    (let [userid "user-with-org"]
      (is (= nil (:name (rems.db.users/get-user userid))))
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
              :organizations [{:organization/id "abc"}]} (rems.db.users/get-user userid))))))

(deftest test-users-api-security
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
    (rems.service.users/add-user! {:userid "user-owner"})
    (rems.db.roles/add-role! "user-owner" :user-owner)
    (-> (request :post (str "/api/users/create"))
        (json-body {:userid "test1"
                    :email "test1@example.com"
                    :name "Test 1"})
        (authenticate +test-api-key+ "user-owner")
        handler
        assert-response-is-ok)))

(deftest test-active-api
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

(deftest test-user-mapping
  (with-redefs [rems.config/env (assoc rems.config/env :oidc-userid-attributes [{:attribute "sub" :rename "elixirId"}
                                                                                {:attribute "old_sub"}])]
    (with-fake-login-users {"alice" {:sub "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                            "elixir-alice" {:sub "elixir-alice" :old_sub "alice" :name "Elixir Alice" :email "alice@elixir-europe.org"}}
      (is (= [] (rems.db.user-mappings/get-user-mappings {:ext-id-attribute "elixirId" :ext-id-value "elixir-alice"})) "user mapping should not exist")

      (testing "log in alice"
        (let [cookie (login-with-cookies "alice")]
          (assert-can-make-a-request! cookie)
          (is (= [{:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}]
                 (api-call :get "/api/users/active" nil
                           +test-api-key+ "owner")))
          (is (= {:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                 (rems.db.users/get-user "alice")
                 (:identity (middleware/get-session cookie))))
          (is (= #{{:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}}
                 (set (api-call :get "/api/users/active" nil
                                +test-api-key+ "owner")))
              "alice shows as active")))

      (testing "log in elixir-alice and create user mapping"
        (is (= [] (rems.db.user-mappings/get-user-mappings {:ext-id-attribute "elixirId" :ext-id-value "elixir-alice"})) "user mapping should not exist")
        (let [cookie (login-with-cookies "elixir-alice")]
          (assert-can-make-a-request! cookie)
          (is (= [{:userid "alice"
                   :ext-id-value "elixir-alice"
                   :ext-id-attribute "elixirId"}]
                 (rems.db.user-mappings/get-user-mappings {:ext-id-attribute "elixirId" :ext-id-value "elixir-alice"})))
          (is (= {:userid "alice" :name "Elixir Alice" :email "alice@elixir-europe.org"}
                 (rems.db.users/get-user "alice")
                 (:identity (middleware/get-session cookie)))
              "Attributes should be updated when logging in")
          (is (= #{{:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                   {:userid "alice" :name "Elixir Alice" :email "alice@elixir-europe.org"}}
                 (set (api-call :get "/api/users/active" nil
                                +test-api-key+ "owner")))
              "both alices show as active"))))

    (with-fake-login-users {"elixir-alice" {:sub "elixir-alice" :name "Elixir Alice" :email "alice@elixir-europe.org"}}
      (testing "log in elixir-alice with user mapping, no old_sub"
        (is (= [{:userid "alice"
                 :ext-id-value "elixir-alice"
                 :ext-id-attribute "elixirId"}]
               (rems.db.user-mappings/get-user-mappings {:ext-id-attribute "elixirId" :ext-id-value "elixir-alice"})))
        (let [cookie (login-with-cookies "elixir-alice")]
          (assert-can-make-a-request! cookie)
          (is (= #{{:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                   {:userid "alice" :name "Elixir Alice" :email "alice@elixir-europe.org"}}
                 (set (api-call :get "/api/users/active" nil
                                +test-api-key+ "owner")))
              "both alices show as active"))))

    (testing "mappings create, get, delete"
      (rems.db.user-mappings/create-user-mapping! {:userid "alice"
                                                   :ext-id-value "alice-alt-id"
                                                   :ext-id-attribute "alt-id"})
      (rems.db.user-mappings/create-user-mapping! {:userid "alice"
                                                   :ext-id-value "alice-alt-id"
                                                   :ext-id-attribute "alt-id2"})
      (is (= [{:userid "alice"
               :ext-id-value "alice-alt-id"
               :ext-id-attribute "alt-id"}
              {:userid "alice"
               :ext-id-value "alice-alt-id"
               :ext-id-attribute "alt-id2"}
              {:userid "alice"
               :ext-id-value "elixir-alice"
               :ext-id-attribute "elixirId"}]
             (sort-by :ext-id-attribute (rems.db.user-mappings/get-user-mappings {:userid "alice"}))))
      (is (= [{:userid "alice"
               :ext-id-value "elixir-alice"
               :ext-id-attribute "elixirId"}]
             (rems.db.user-mappings/get-user-mappings {:ext-id-value "elixir-alice"})))
      (is (= [{:userid "alice"
               :ext-id-value "alice-alt-id"
               :ext-id-attribute "alt-id"}
              {:userid "alice"
               :ext-id-value "alice-alt-id"
               :ext-id-attribute "alt-id2"}]
             (sort-by :ext-id-attribute (rems.db.user-mappings/get-user-mappings {:ext-id-value "alice-alt-id"}))))

      (rems.db.user-mappings/delete-user-mapping! {:userid "unrelated"}) ; should not affect tested data

      (is (= [{:userid "alice"
               :ext-id-value "alice-alt-id"
               :ext-id-attribute "alt-id"}
              {:userid "alice"
               :ext-id-value "alice-alt-id"
               :ext-id-attribute "alt-id2"}
              {:userid "alice"
               :ext-id-value "elixir-alice"
               :ext-id-attribute "elixirId"}]
             (sort-by :ext-id-attribute (rems.db.user-mappings/get-user-mappings {:userid "alice"}))))
      (is (= [{:userid "alice"
               :ext-id-value "elixir-alice"
               :ext-id-attribute "elixirId"}]
             (rems.db.user-mappings/get-user-mappings {:ext-id-value "elixir-alice"})))
      (is (= [{:userid "alice"
               :ext-id-value "alice-alt-id"
               :ext-id-attribute "alt-id"}
              {:userid "alice"
               :ext-id-value "alice-alt-id"
               :ext-id-attribute "alt-id2"}]
             (sort-by :ext-id-attribute (rems.db.user-mappings/get-user-mappings {:ext-id-value "alice-alt-id"}))))

      (rems.db.user-mappings/delete-user-mapping! {:userid "alice"})

      (is (= [] (rems.db.user-mappings/get-user-mappings {:userid "alice"})))
      (is (= [] (rems.db.user-mappings/get-user-mappings {:ext-id-value "elixir-alice"})))
      (is (= [] (rems.db.user-mappings/get-user-mappings {:ext-id-value "alice-alt-id"}))))))


(deftest test-user-name
  (with-redefs [rems.config/env (assoc rems.config/env :oidc-name-attributes ["name" "name2"])]
    (with-fake-login-users {"alice" {:sub "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                            "bob" {:sub "bob" :name2 "Bob Applicant" :email "bob@example.com"}
                            "malice" {:sub "malice" :email "malice@example.com"}} ; no name
      (testing "log in alice"
        (let [cookie (login-with-cookies "alice")]
          (assert-can-make-a-request! cookie)
          (is (= {:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                 (rems.db.users/get-user "alice")
                 (:identity (middleware/get-session cookie))))))

      (testing "log in bob"
        (let [cookie (login-with-cookies "bob")]
          (assert-can-make-a-request! cookie)
          (is (= {:userid "bob" :name "Bob Applicant" :email "bob@example.com"}
                 (rems.db.users/get-user "bob")
                 (:identity (middleware/get-session cookie))))))

      (testing "log in malice"
        (is (thrown? AssertionError (login-with-cookies "malice"))
            "name should be required")))))

(deftest test-user-email
  (with-fake-login-users {"alice" {:sub "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                          "bob" {:sub "bob" :name "Bob Applicant" :email2 "bob@example.com"}
                          "malice" {:sub "malice" :name "Malice Nomail"}} ; no email
    (with-redefs [rems.config/env (assoc rems.config/env :oidc-email-attributes ["email" "email2"])]
      (testing "log in malice"
        (let [cookie (login-with-cookies "malice")]
          (assert-can-make-a-request! cookie)
          (is (= {:userid "malice" :name "Malice Nomail" :email nil}
                 (rems.db.users/get-user "malice")
                 (:identity (middleware/get-session cookie)))
              "no email is ok without validation"))))

    (with-redefs [rems.config/env (assoc rems.config/env
                                         :oidc-email-attributes ["email" "email2"]
                                         :oidc-require-email true)]
      (testing "log in alice"
        (let [cookie (login-with-cookies "alice")]
          (assert-can-make-a-request! cookie)
          (is (= {:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"}
                 (rems.db.users/get-user "alice")
                 (:identity (middleware/get-session cookie)))
              "normal user is fine")))

      (testing "log in bob"
        (let [cookie (login-with-cookies "bob")]
          (assert-can-make-a-request! cookie)
          (is (= {:userid "bob" :name "Bob Applicant" :email "bob@example.com"}
                 (rems.db.users/get-user "bob")
                 (:identity (middleware/get-session cookie)))
              "secondary email attribute is used")))

      (testing "log in malice with requirement"
        (is (thrown? AssertionError (login-with-cookies "malice"))
            "email should be required")))))
