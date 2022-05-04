(ns ^:integration rems.api.test-api
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.api-key :as api-key]
            [rems.db.core :as db]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures :once api-fixture)

(deftest test-api-not-found
  (testing "unknown endpoint"
    (let [resp (-> (request :get "/api/unknown")
                   handler)]
      (is (response-is-not-found? resp))))
  (testing "known endpoint, wrong method,"
    (testing "unauthorized"
      (let [resp (-> (request :get "/api/blacklist/remove")
                     handler)]
        (is (response-is-not-found? resp))))
    (testing "authorized,"
      (testing "missing params"
        ;; Surprisingly hard to find a POST route that isn't shadowed
        ;; by a GET route. For example, GET /api/applications/command
        ;; hits the /api/applications/:application-id route.
        (let [resp (-> (request :get "/api/blacklist/remove")
                       (authenticate "42" "handler")
                       handler)]
          (is (response-is-not-found? resp)))))))

(deftest test-api-key-security
  (api-key/add-api-key! "42" {})
  (test-helpers/create-user! {:userid "alice"})
  (test-helpers/create-user! {:userid "owner"} :owner)
  (testing ":api-key role"
    (testing "available for valid api key"
      (is (response-is-ok? (-> (request :post "/api/email/send-reminders")
                               (authenticate "42" "owner")
                               handler))))
    (testing "not available when using wrong API key"
      (let [username "alice"
            ;; need cookies and csrf to actually get a forbidden instead of "invalid csrf token"
            ;; TODO check this
            cookie (login-with-cookies username)
            csrf (get-csrf-token cookie)
            resp (-> (request :post "/api/email/send-reminders")
                     (header "Cookie" cookie)
                     (header "x-csrf-token" csrf)
                     (header "x-rems-api-key" "WRONG")
                     handler)]
        (is (response-is-forbidden? resp)))))
  (testing "api key user whitelist"
    (api-key/add-api-key! "43" {:comment "all users" :users nil})
    (api-key/add-api-key! "44" {:comment "only alice & malice" :users ["alice" "malice"]})
    (testing "> api key without whitelist can impersonate any user >"
      (doseq [user ["owner" "alice" "malice"]]
        (testing user
          (is (response-is-ok? (api-response :get "/api/catalogue/" nil
                                             "43" user))))))
    (testing "> api key with whitelist can only impersonate given users >"
      (doseq [user ["alice" "malice"]]
        (testing user
          (is (response-is-ok? (api-response :get "/api/my-applications/" nil
                                             "44" user)))
          (is (response-is-unauthorized? (api-response :get "/api/my-applications/" nil
                                                       "44" "owner")))))))
  (testing "api key path whitelist"
    (api-key/add-api-key! "45" {:comment "all paths" :paths nil})
    (api-key/add-api-key! "46" {:comment "limited paths" :paths [{:method "any"
                                                                  :path "/api/applications"}
                                                                 {:path "/api/my-applications"
                                                                  :method "any"}]})
    (api-key/add-api-key! "47" {:comment "regex path" :paths [{:method "any"
                                                               :path "/api/c.*"}
                                                              {:method "get"
                                                               :path "/api/users/.*"}]})

    (testing "> api key without whitelist can access any path >"
      (doseq [path ["/api/applications" "/api/my-applications"]]
        (testing path
          (is (response-is-ok? (api-response :get path nil
                                             "45" "owner"))))))
    (testing "> api key with whitelist can access only given paths >"
      (doseq [path ["/api/applications" "/api/my-applications"]]
        (testing path
          (is (response-is-ok? (api-response :get path nil
                                             "46" "owner")))))
      (is (response-is-unauthorized? (api-response :get "/api/catalogue-items" nil
                                                   "46" "owner"))))
    (testing "> api key with whitelist can access only matching paths >"
      (doseq [path ["/api/catalogue?query=param" "/api/catalogue-items"]]
        (testing path
          (is (response-is-ok? (api-response :get path nil
                                             "47" "owner")))))
      (is (response-is-unauthorized? (api-response :get "/api/applications" nil
                                                   "47" "owner"))))
    (testing "> api key with whitelist can use only matching methods"
      (is (response-is-ok? (api-response :get "/api/users/active" nil
                                         "47" "owner")))
      (is (response-is-unauthorized? (api-response :post "/api/users/create"
                                                   {:userid "testing"
                                                    :name nil
                                                    :email nil}
                                                   "47" "owner"))))))

(deftest test-health-api
  ;; create at least one event
  (test-helpers/create-user! {:userid "alice"})
  (test-helpers/create-application! {:actor "alice"})
  (let [body (-> (request :get "/api/health")
                 handler
                 read-ok-body)]
    (is (:healthy body))
    (is (string? (:latest-event body)))
    (is (not (empty? (:latest-event body))))))

(deftest test-keepalive-api
  (assert-response-is-ok (-> (request :get "/keepalive")
                             handler))
  (is true))

(deftest data-exception-test
  (api-key/add-api-key! "42" {})
  (test-helpers/create-user! {:userid "owner"} :owner)
  (testing "a broken license without an organization"
    (let [license-id (:id (db/create-license! {:organization "does-not-exist"
                                               :type "text"}))
          response (-> (api-response :get (str "/api/licenses/" license-id)
                                     nil
                                     "42" "owner"))]
      (testing "returns a useful description of the problem"
        (is (= 503 (:status response)))
        (is (= {:errors [{:args ["does-not-exist"]
                          :organization/id "does-not-exist"
                          :type "t.actions.errors/organization-does-not-exist"}]}
               (read-body response)))))))
