(ns ^:integration rems.api.test-email
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.outbox :as outbox]
            [rems.db.test-data :as test-data]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest test-send-handler-reminder
  (let [api-key "42"
        user-id "developer"
        outbox-emails (atom [])]

    (testing "sends emails"
      (with-redefs [outbox/put! (fn [email]
                                  (swap! outbox-emails conj email))]
        (let [body (-> (request :post "/api/email/send-handler-reminder")
                       (authenticate api-key user-id)
                       handler
                       read-ok-body)]
          (is (= "OK" body))
          (is (= [{:subject "Applications in progress", :to-user "developer"}
                  {:subject "Applications in progress", :to-user "handler"}]
                 (->> @outbox-emails
                      (map #(select-keys (:outbox/email %) [:subject :to-user]))
                      (sort-by :to-user)))))))

    (testing "requires API key"
      (let [cookie (login-with-cookies user-id)
            csrf (get-csrf-token cookie)
            response (-> (request :post "/api/email/send-handler-reminder")
                         (header "Cookie" cookie)
                         (header "x-csrf-token" csrf)
                         handler)]
        (is (response-is-forbidden? response))
        (is (str/includes? (get-in response [:headers "x-rems-roles"])
                           "logged-in"))))))

(deftest test-send-reviewer-reminder
  (let [api-key "42"
        user-id "developer"
        outbox-emails (atom [])

        app-id (test-data/create-application! {:actor "alice"})]
    (test-data/command! {:type :application.command/submit
                         :application-id app-id
                         :actor "alice"})
    (test-data/command! {:type :application.command/request-comment
                         :application-id app-id
                         :actor "developer"
                         :commenters ["carl"]
                         :comment ""})

    (testing "sends emails"
      (with-redefs [outbox/put! (fn [email]
                                  (swap! outbox-emails conj email))]
        (let [body (-> (request :post "/api/email/send-reviewer-reminder")
                       (authenticate api-key user-id)
                       handler
                       read-ok-body)]
          (is (= "OK" body))
          (is (= [{:subject "Applications pending review", :to-user "carl"}]
                 (->> @outbox-emails
                      (map #(select-keys (:outbox/email %) [:subject :to-user]))
                      (sort-by :to-user)))))))

    (testing "requires API key"
      (let [cookie (login-with-cookies user-id)
            csrf (get-csrf-token cookie)
            response (-> (request :post "/api/email/send-reviewer-reminder")
                         (header "Cookie" cookie)
                         (header "x-csrf-token" csrf)
                         handler)]
        (is (response-is-forbidden? response))
        (is (str/includes? (get-in response [:headers "x-rems-roles"])
                           "logged-in"))))))
