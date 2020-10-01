(ns ^:integration rems.api.test-email
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.outbox :as outbox]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(def api-key "42")
(def user-id "developer")

(defn- create-application-in-review! []
  (let [app-id (test-helpers/create-application! {:actor "alice"})]
    (test-helpers/command! {:type :application.command/submit
                            :application-id app-id
                            :actor "alice"})
    (test-helpers/command! {:type :application.command/request-review
                            :application-id app-id
                            :actor "developer"
                            :reviewers ["carl"]
                            :comment ""})))

(deftest test-send-handler-reminder
  (testing "sends emails"
    (let [outbox-emails (atom [])]
      (with-redefs [outbox/put! (fn [email]
                                  (swap! outbox-emails conj email))]
        (let [body (-> (request :post "/api/email/send-handler-reminder")
                       (authenticate api-key user-id)
                       handler
                       read-ok-body)]
          (is (= "OK" body))
          (is (= [{:subject "Applications in progress", :to-user "developer"}
                  {:subject "Applications in progress", :to-user "handler"}
                  {:subject "Applications in progress", :to-user "rejecter-bot"}]
                 (->> @outbox-emails
                      (map #(select-keys (:outbox/email %) [:subject :to-user]))
                      (sort-by :to-user))))))))

  (testing "requires API key"
    (let [response (-> (request :post "/api/email/send-handler-reminder")
                       (add-login-cookies user-id)
                       handler)]
      (is (response-is-forbidden? response))
      (is (logged-in? response)))))

(deftest test-send-reviewer-reminder
  (create-application-in-review!)

  (testing "sends emails"
    (let [outbox-emails (atom [])]
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
                      (sort-by :to-user))))))))

  (testing "requires API key"
    (let [response (-> (request :post "/api/email/send-reviewer-reminder")
                       (add-login-cookies user-id)
                       handler)]
      (is (response-is-forbidden? response))
      (is (logged-in? response)))))

(deftest test-send-reminders
  (create-application-in-review!)

  (testing "sends emails"
    (let [outbox-emails (atom [])]
      (with-redefs [outbox/put! (fn [email]
                                  (swap! outbox-emails conj email))]
        (let [body (-> (request :post "/api/email/send-reminders")
                       (authenticate api-key user-id)
                       handler
                       read-ok-body)]
          (is (= "OK" body))
          (is (= [{:subject "Applications pending review", :to-user "carl"}
                  {:subject "Applications in progress", :to-user "developer"}
                  {:subject "Applications in progress", :to-user "handler"}
                  {:subject "Applications in progress", :to-user "rejecter-bot"}]
                 (->> @outbox-emails
                      (map #(select-keys (:outbox/email %) [:subject :to-user]))
                      (sort-by :to-user))))))))

  (testing "requires API key"
    (let [response (-> (request :post "/api/email/send-reminders")
                       (add-login-cookies user-id)
                       handler)]
      (is (response-is-forbidden? response))
      (is (logged-in? response)))))
