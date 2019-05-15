(ns ^:integration rems.api.test-entitlements
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [rems.poller.entitlements :as entitlements-poller]
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
  (entitlements-poller/run)
  (let [api-key "42"]
    (testing "all"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "developer")
                     handler
                     read-body)]
        (is (= 2 (count data)))))

    (testing "all as an developer"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "developer")
                     handler
                     read-body)]
        (is (= 2 (count data)))
        ;; sanity check the data
        (is (= {:resource "urn:nbn:fi:lb-201403262"
                :application-id 12
                :mail "alice@example.com"}
               (-> data first (dissoc :start))))
        (is (= {:resource "urn:nbn:fi:lb-201403262"
                :application-id 19
                :mail "developer@example.com"}
               (-> data second (dissoc :start))))))

    (testing "all as an owner"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "owner")
                     handler
                     read-body)]
        (is (= 2 (count data)))
        ;; sanity check the data
        (is (= {:resource "urn:nbn:fi:lb-201403262"
                :application-id 12
                :mail "alice@example.com"}
               (-> data first (dissoc :start))))
        (is (= {:resource "urn:nbn:fi:lb-201403262"
                :application-id 19
                :mail "developer@example.com"}
               (-> data second (dissoc :start))))))

    (testing "all as a reporter"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "reporter")
                     handler
                     read-body)]
        (is (= 2 (count data)))
        ;; sanity check the data
        (is (= {:resource "urn:nbn:fi:lb-201403262"
                :application-id 12
                :mail "alice@example.com"}
               (-> data first (dissoc :start))))
        (is (= {:resource "urn:nbn:fi:lb-201403262"
                :application-id 19
                :mail "developer@example.com"}
               (-> data second (dissoc :start))))))

    (testing "just for alice as a developer"
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

    (testing "just for alice as an owner"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "owner")
                     handler
                     read-body)]
        (is (= 1 (count data)))
        ;; sanity check the data
        (is (= {:resource "urn:nbn:fi:lb-201403262"
                :application-id 12
                :mail "alice@example.com"}
               (-> data first (dissoc :start))))))

    (testing "just for alice as a reporter"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "reporter")
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
