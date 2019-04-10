(ns ^:integration rems.api.test-entitlements
  (:require [clojure.test :refer :all]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [rems.db.users :as users]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest entitlements-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/entitlements"))
                       handler)
          body (read-body response)]
      (is (= "unauthorized" body)))))

(deftest entitlements-test
  (rems.poller.entitlements/run)
  (let [api-key "42"]
    (testing "all"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "developer")
                     handler
                     read-body)]
        (is (= 2 (count data)))))

    (testing "just for alice"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "developer")
                     handler
                     read-body)]
        (is (= 1 (count data)))
        ;; sanity check the data
        (is (= {:resource "urn:nbn:fi:lb-201403262"
                :application-id 12
                :mail "alice@example.com"}
               (-> data first (dissoc :start))))))

    (testing "listing as applicant"
      (testing "with entitlements"
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "alice")
                       handler
                       assert-response-is-ok
                       read-body)]
          (is (coll-is-not-empty? body))
          (is (every? #(= (:mail %) "alice@example.com") body))))

      (testing "without entitlements"
        (users/add-user! "allison" {})
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "allison")
                       handler
                       assert-response-is-ok
                       read-body)]
          (is (coll-is-empty? body)))))))
