(ns ^:integration rems.api.test-api
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.core :as db]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures :each api-fixture)

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

(deftest test-api-key-roles
  (testing "API key roles"
    (testing "all available"
      (let [resp (-> (request :get "/api/forms")
                     (authenticate "42" "owner")
                     handler)]
        (is (= 200 (:status resp)))))
    (testing "handler and owner roles unavailable"
      (let [resp (-> (request :get "/api/forms")
                     (authenticate "43" "owner")
                     handler)]
        (is (response-is-forbidden? resp)))))
  (testing ":api-key role not available when using wrong API key"
    (let [username "alice"
          cookie (login-with-cookies username)
          csrf (get-csrf-token cookie)
          resp (-> (request :post "/api/email/send-reminders")
                   (header "Cookie" cookie)
                   (header "x-csrf-token" csrf)
                   (header "x-rems-api-key" "WRONG")
                   handler)]
      (is (response-is-forbidden? resp)))))

(deftest test-health-api
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

(deftest test-audit-log
  (testing "populate log"
    (testing "> unknown endpoint"
      (testing "> no user"
        (is (response-is-not-found? (-> (request :get "/api/unknown")
                                        handler))))
      (testing "> valid api-key and user"
        (is (response-is-not-found? (-> (request :get "/api/unknown")
                                        (authenticate "42" "owner")
                                        handler)))))
    (testing "> known endpoint"
      (testing "> api key"
        (testing "> GET"
          (testing "> unauthorized"
            (is (response-is-forbidden? (-> (request :get "/api/users/active")
                                            (authenticate "42" "alice")
                                            handler))))
          (testing "> authorized"
            (is (= 200 (:status (-> (request :get "/api/users/active")
                                    (authenticate "42" "owner")
                                    handler))))))
        (testing "> POST"
          (testing "> status 200, different api key"
            (is (false? (:success (-> (request :post "/api/applications/submit")
                                      (authenticate "43" "alice")
                                      (json-body {:application-id 99999999999})
                                      handler
                                      read-ok-body)))))
          (testing "> status 400"
            (is (response-is-bad-request? (-> (request :post "/api/applications/submit")
                                              (authenticate "42" "alice")
                                              (json-body {:boing "3"})
                                              handler))))
          (testing "> status 500"
            (with-redefs [rems.api.services.command/command! (fn [_] (throw (Error. "BOOM")))]
              (is (response-is-server-error? (-> (request :post "/api/applications/submit")
                                                 (authenticate "42" "alice")
                                                 (json-body {:application-id 3})
                                                 handler)))))))
      (testing "> session"
        (let [cookie (login-with-cookies "malice")
              csrf (get-csrf-token cookie)]
          (testing "> GET"
            (is (= 200 (:status (-> (request :get "/api/catalogue")
                                    (header "Cookie" cookie)
                                    (header "x-csrf-token" csrf)
                                    handler)))))
          (testing "> failed PUT"
            (is (response-is-forbidden? (-> (request :put "/api/catalogue-items/archived")
                                            (header "Cookie" cookie)
                                            (header "x-csrf-token" csrf)
                                            (json-body {:id 9999999 :archived true})
                                            handler))))))))

  (testing "check log"
    (is (= [{:userid nil :apikey nil :method "get" :path "/api/unknown" :status "404"}
            {:userid "owner" :apikey "42" :method "get" :path "/api/unknown" :status "404"}
            {:userid "alice" :apikey "42" :method "get" :path "/api/users/active" :status "403"}
            {:userid "owner" :apikey "42" :method "get" :path "/api/users/active" :status "200"}
            {:userid "alice" :apikey "43" :method "post" :path "/api/applications/submit" :status "200"}
            {:userid "alice" :apikey "42" :method "post" :path "/api/applications/submit" :status "400"}
            {:userid "alice" :apikey "42" :method "post" :path "/api/applications/submit" :status "500"}
            {:userid "malice" :apikey nil :method "get" :path "/api/catalogue" :status "200"}
            {:userid "malice" :apikey nil :method "put" :path "/api/catalogue-items/archived" :status "403"}]
           (map #(select-keys % [:userid :apikey :method :path :status]) (db/get-audit-log {}))))))
