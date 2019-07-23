(ns ^:integration rems.api.test-entitlements
  (:require [clj-time.format :as time-format]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [rems.poller.entitlements :as entitlements-poller]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(defn- valid-date? [x]
  (and (string? x)
       (time-format/parse (time-format/formatters :date-time) x)))

(deftest entitlements-test
  ;; TODO: create applications inside the test
  (entitlements-poller/run)
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/entitlements"))
                       handler)
          body (read-body response)]
      (is (= "unauthorized" body))))
  (let [api-key "42"
        check-alice-entitlement (fn [x]
                                  (is (= {:resource "urn:nbn:fi:lb-201403262"
                                          :end nil
                                          :mail "alice@example.com"}
                                         (dissoc x :start :application-id)))
                                  (is (valid-date? (:start x))))
        check-alice-expired-entitlement (fn [x]
                                          (is (= {:resource "urn:nbn:fi:lb-201403262"
                                                  :mail "alice@example.com"}
                                                 (dissoc x :start :end :application-id)))
                                          (is (valid-date? (:start x)))
                                          (is (valid-date? (:end x))))
        check-developer-entitlement (fn [x]
                                      (is (= {:resource "urn:nbn:fi:lb-201403262"
                                              :end nil
                                              :mail "developer@example.com"}
                                             (dissoc x :start :application-id)))
                                      (is (valid-date? (:start x))))]
    (testing "all"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "developer")
                     handler
                     read-body)]
        (is (= 2 (count data)))
        (check-alice-entitlement (first data))
        (check-developer-entitlement (second data))))

    (doseq [userid ["developer" "owner" "reporter"]]
      (testing (str "all as " userid)
        (let [data (-> (request :get "/api/entitlements")
                       (authenticate api-key userid)
                       handler
                       read-body)]
          (is (= 2 (count data)))
          (check-alice-entitlement (first data))
          (check-developer-entitlement (second data)))))

    (testing "just for alice as a developer"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "developer")
                     handler
                     read-body)]
        (is (= 1 (count data)))
        (check-alice-entitlement (first data))))

    (testing "just for alice as an owner"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "owner")
                     handler
                     read-body)]
        (is (= 1 (count data)))
        (check-alice-entitlement (first data))))

    (testing "also expired / ended as an owner"
      (let [data (-> (request :get "/api/entitlements?expired=true")
                     (authenticate api-key "owner")
                     handler
                     read-body)]
        (is (= 3 (count data)))
        (check-alice-entitlement (first data))
        (check-alice-expired-entitlement (second data))
        (check-developer-entitlement (nth data 2))))

    (testing "just for alice as a reporter"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "reporter")
                     handler
                     read-body)]
        (is (= 1 (count data)))
        (check-alice-entitlement (first data))))

    (testing "listing as applicant"
      (testing "with entitlements"
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "alice")
                       handler
                       assert-response-is-ok
                       read-body)]
          (is (coll-is-not-empty? body))
          (doseq [x body]
            (check-alice-entitlement x))))

      (testing "without entitlements"
        (users/add-user! "allison" {})
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "allison")
                       handler
                       assert-response-is-ok
                       read-body)]
          (is (coll-is-empty? body)))))))
