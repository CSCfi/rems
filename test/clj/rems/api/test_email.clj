(ns ^:integration rems.api.test-email
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.outbox :as outbox]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.service.test-data :as test-data]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture)

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

(defn- create-application-in-progress! []
  (let [wf (test-helpers/create-workflow! {:handlers ["developer" "handler"]})
        cat-id (test-helpers/create-catalogue-item! {:workflow-id wf})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id] :actor "alice"})]
    (test-helpers/command! {:type :application.command/submit
                            :application-id app-id
                            :actor "alice"})))

(deftest test-send-handler-reminder
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (create-application-in-progress!)
  (testing "sends emails"
    (let [outbox-emails (atom [])]
      (with-redefs [outbox/put! (fn [email]
                                  (swap! outbox-emails conj email))]
        (let [body (-> (request :post "/api/email/send-handler-reminder")
                       (authenticate test-data/+test-api-key+ "developer")
                       handler
                       read-ok-body)]
          (is (= "OK" body))
          (is (= [{:subject "Applications in progress", :to-user "developer"}
                  {:subject "Applications in progress", :to-user "handler"}]
                 (->> @outbox-emails
                      (map #(select-keys (:outbox/email %) [:subject :to-user]))
                      (sort-by :to-user))))))))

  (testing "requires API key"
    (let [response (-> (request :post "/api/email/send-handler-reminder")
                       (add-login-cookies "developer")
                       handler)]
      (is (response-is-forbidden? response))
      (is (logged-in? response)))))

(deftest test-send-reviewer-reminder
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (create-application-in-review!)

  (testing "sends emails"
    (let [outbox-emails (atom [])]
      (with-redefs [outbox/put! (fn [email]
                                  (swap! outbox-emails conj email))]
        (let [body (-> (request :post "/api/email/send-reviewer-reminder")
                       (authenticate test-data/+test-api-key+ "developer")
                       handler
                       read-ok-body)]
          (is (= "OK" body))
          (is (= [{:subject "Applications pending review", :to-user "carl"}]
                 (->> @outbox-emails
                      (map #(select-keys (:outbox/email %) [:subject :to-user]))
                      (sort-by :to-user))))))))

  (testing "requires API key"
    (let [response (-> (request :post "/api/email/send-reviewer-reminder")
                       (add-login-cookies "developer")
                       handler)]
      (is (response-is-forbidden? response))
      (is (logged-in? response)))))

(deftest test-send-reminders
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (create-application-in-review!)
  (create-application-in-progress!)

  (testing "sends emails"
    (let [outbox-emails (atom [])]
      (with-redefs [outbox/put! (fn [email]
                                  (swap! outbox-emails conj email))]
        (let [body (-> (request :post "/api/email/send-reminders")
                       (authenticate test-data/+test-api-key+ "developer")
                       handler
                       read-ok-body)]
          (is (= "OK" body))
          (is (= [{:subject "Applications pending review", :to-user "carl"}
                  {:subject "Applications in progress", :to-user "developer"}
                  {:subject "Applications in progress", :to-user "handler"}]
                 (->> @outbox-emails
                      (map #(select-keys (:outbox/email %) [:subject :to-user]))
                      (sort-by :to-user))))))))

  (testing "requires API key"
    (let [response (-> (request :post "/api/email/send-reminders")
                       (add-login-cookies "developer")
                       handler)]
      (is (response-is-forbidden? response))
      (is (logged-in? response)))))
