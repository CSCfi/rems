(ns ^:integration rems.api.test-user-settings
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clj-time.core :as time-core]
            [rems.api.testing :refer :all]
            [rems.service.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.user-secrets :as user-secrets]
            [rems.db.user-settings :as user-settings]
            [rems.handler :refer [handler]]
            [rems.json :as json]
            [rems.testing-util :refer [with-fixed-time with-fake-login-users]]
            [ring.mock.request :refer :all]
            [stub-http.core :as stub])
  (:import [java.util UUID]))

(use-fixtures
  :once
  api-fixture)

(deftest user-settings-api-test
  (test-data/create-test-api-key!)
  (let [user-id (str (UUID/randomUUID))]
    (test-helpers/create-user! {:userid user-id})

    (testing "default user settings without authentification"
      (is (= {:status 401
              :body "unauthorized"}
             (-> (request :get "/api/user-settings")
                 handler
                 read-body-and-status))))

    (testing "default user settings"
      (is (= {:language "en"
              :notification-email nil}
             (-> (request :get "/api/user-settings")
                 (authenticate "42" user-id)
                 handler
                 read-ok-body))))

    (testing "update user settings"
      (-> (request :put "/api/user-settings/edit")
          (json-body {:language :fi})
          (authenticate "42" user-id)
          handler
          assert-response-is-ok)
      (is (= {:language "fi"
              :notification-email nil}
             (-> (request :get "/api/user-settings")
                 (authenticate "42" user-id)
                 handler
                 read-ok-body))))))

(defn ega-config [server]
  {:type :ega
   :connect-server-url (str (:uri server) "/c")
   :permission-server-url (str (:uri server) "/p")})

(defn run-with-ega-server
  [spec callback]
  (with-open [server (stub/start! spec)]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :entitlement-push [(ega-config server)])]
      (callback server))))

(comment
  ;; small helper to test the EGA API / REMS UI
  ;; configure this to the dev-config.edn or so
  :entitlement-push [{:id :stub-ega
                      :type :ega
                      :permission-server-url "http://localhost:1234/p"}]
  (def ega-stub-server
    (stub/start! {:port 1234}
                 {{:path "/p/api_key/handler" :method "DELETE"} {:status 200 :content-type "application/json"}
                  "/p/api_key/generate" {:status 200 :content-type "application/json" :body (json/generate-string {:token "access-token-xyz"})}}))
  (.close ega-stub-server))

(deftest test-generate-api-key
  (test-data/create-test-api-key!)
  (let [user-id (str (UUID/randomUUID))]
    (test-helpers/create-user! {:userid user-id :name "Test User" :email "test.user@example.com"})

    (testing "without authentication"
      (let [{:keys [body] :as response} (-> (request :post "/api/user-settings/generate-ega-api-key")
                                            handler
                                            read-body-and-status)]
        (is (response-is-unauthorized? response))
        (is (str/includes? body "Invalid anti-forgery token"))))

    (testing "not a handler"
      (is (= {:status 403
              :body "forbidden"}
             (-> (request :post "/api/user-settings/generate-ega-api-key")
                 (authenticate "42" user-id)
                 handler
                 read-body-and-status))))

    (test-helpers/create-workflow! {:handlers [user-id]})

    (testing "without entitlement push configured"
      (is (= {:status 500
              :body {:type "unknown-exception" :class "java.lang.AssertionError"}}
             (-> (request :post "/api/user-settings/generate-ega-api-key")
                 (authenticate "42" user-id)
                 handler
                 read-body-and-status))))

    (testing "success"
      (with-fake-login-users {user-id {:sub user-id :name "Test User" :email "test.user@example.com"}}
        (with-fixed-time (time-core/date-time 2021)
          (fn []
            (run-with-ega-server
             {"/p/api_key/generate" {:status 200 :content-type "application/json" :body (json/generate-string {:token "access-token-xyz"})}}
             (fn [_server]
               (let [cookie (login-with-cookies user-id)
                     csrf (get-csrf-token cookie)]
                 (testing "generate-ega-api-key with session"
                   (is (= {:status 200
                           :body {:success true
                                  :api-key-expiration-date "2022-01-01T00:00:00.000Z"}} ; one year expiry
                          (-> (request :post "/api/user-settings/generate-ega-api-key")
                              (header "Cookie" cookie)
                              (header "x-csrf-token" csrf)
                              handler
                              assert-response-is-ok
                              read-body-and-status)))
                   (is (= (time-core/date-time 2022)
                          (get-in (user-settings/get-user-settings user-id) [:ega :api-key-expiration-date]))
                       "one year expiry")
                   (is (= {:ega {:api-key "access-token-xyz"}}
                          (user-secrets/get-user-secrets user-id)))))))))))))

(deftest test-delete-api-key
  (test-data/create-test-api-key!)
  (let [user-id (str (UUID/randomUUID))
        handler-id (str (UUID/randomUUID))]
    (test-helpers/create-user! {:userid user-id :name "Test User" :email "test.user@example.com"})
    (test-helpers/create-user! {:userid handler-id :name "Handler" :email "handler@example.com"})
    (test-helpers/create-workflow! {:handlers [handler-id]})

    (testing "setup api-key to delete"
      (with-fake-login-users {handler-id {:sub handler-id :name "Handler" :email "handler@example.com"}}
        (with-fixed-time (time-core/date-time 2021)
          (fn []
            (run-with-ega-server
             {"/p/api_key/generate" {:status 200 :content-type "application/json" :body (json/generate-string {:token "access-token-xyz"})}}
             (fn [_server]
               (let [cookie (login-with-cookies handler-id)
                     csrf (get-csrf-token cookie)]
                 (testing "generate-ega-api-key with session"
                   (is (= {:status 200
                           :body {:success true
                                  :api-key-expiration-date "2022-01-01T00:00:00.000Z"}}
                          (-> (request :post "/api/user-settings/generate-ega-api-key")
                              (header "Cookie" cookie)
                              (header "x-csrf-token" csrf)
                              handler
                              assert-response-is-ok
                              read-body-and-status)))))))))))

    (testing "without authentication"
      (let [{:keys [body] :as response} (-> (request :post "/api/user-settings/delete-ega-api-key")
                                            handler
                                            read-body-and-status)]
        (is (response-is-unauthorized? response))
        (is (str/includes? body "Invalid anti-forgery token"))))

    (testing "not a handler"
      (is (= {:status 403
              :body "forbidden"}
             (-> (request :post "/api/user-settings/delete-ega-api-key")
                 (authenticate "42" user-id)
                 handler
                 read-body-and-status))))

    (test-helpers/create-workflow! {:handlers [user-id]})

    (testing "without entitlement push configured"
      (is (= {:status 500
              :body {:type "unknown-exception" :class "java.lang.AssertionError"}}
             (-> (request :post "/api/user-settings/delete-ega-api-key")
                 (authenticate "42" user-id)
                 handler
                 read-body-and-status))))

    (testing "success"
      (with-fake-login-users {handler-id {:sub handler-id :name "Handler" :email "handler@example.com"}}
        (with-fixed-time (time-core/date-time 2021)
          (fn []
            (run-with-ega-server
             {{:path (str "/p/api_key/" handler-id) :method "DELETE"} {:status 200 :content-type "application/json" :body ""}}
             (fn [server]
               (let [cookie (login-with-cookies handler-id)
                     csrf (get-csrf-token cookie)]
                 (testing "delete-ega-api-key with session"
                   (is (= {:status 200
                           :body {:success true}}
                          (-> (request :post "/api/user-settings/delete-ega-api-key")
                              (header "Cookie" cookie)
                              (header "x-csrf-token" csrf)
                              handler
                              assert-response-is-ok
                              read-body-and-status)))))))))))))

