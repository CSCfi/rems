(ns ^:integration rems.api.test-entitlements
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest entitlements-test
  ;; TODO: create applications inside the test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/entitlements"))
                       handler)
          body (read-body response)]
      (is (= "unauthorized" body))))
  ;; zero malice's email to test the api with nil emails
  (users/add-user! "malice" {:eppn "malice" :commonName "Malice Applicant"})
  (let [api-key "42"
        check-alice-entitlement (fn [x]
                                  (is (= {:user {:userid "alice"
                                                 :email "alice@example.com"
                                                 :name "Alice Applicant"}
                                          :resource "urn:nbn:fi:lb-201403262"
                                          :end nil
                                          :mail "alice@example.com"}
                                         (dissoc x :start :application-id)))
                                  (is (valid-date? (:start x))))
        check-alice-expired-entitlement (fn [x]
                                          (is (= {:user {:userid "alice"
                                                         :email "alice@example.com"
                                                         :name "Alice Applicant"}
                                                  :resource "urn:nbn:fi:lb-201403262"
                                                  :mail "alice@example.com"}
                                                 (dissoc x :start :end :application-id)))
                                          (is (valid-date? (:start x)))
                                          (is (valid-date? (:end x))))
        check-malice-entitlement (fn [x]
                                   (is (= {:user {:userid "malice"
                                                  :email nil
                                                  :name "Malice Applicant"}
                                           :resource "urn:nbn:fi:lb-201403262"
                                           :end nil
                                           :mail nil}
                                          (dissoc x :start :application-id)))
                                   (is (valid-date? (:start x))))]
    (testing "all"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "developer")
                     handler
                     read-ok-body)]
        (is (= 2 (count data)))
        (check-alice-entitlement (first data))
        (check-malice-entitlement (second data))))

    (doseq [userid ["developer" "owner" "reporter"]]
      (testing (str "all as " userid)
        (let [data (-> (request :get "/api/entitlements")
                       (authenticate api-key userid)
                       handler
                       read-ok-body)]
          (is (= 2 (count data)))
          (check-alice-entitlement (first data))
          (check-malice-entitlement (second data)))))

    (testing "for one resource"
      (let [data (-> (request :get "/api/entitlements?resource=urn:nbn:fi:lb-201403262")
                     (authenticate api-key "developer")
                     handler
                     read-ok-body)]
        (is (= 2 (count data)))
        (check-alice-entitlement (first data))
        (check-malice-entitlement (second data))))

    (testing "just for alice as a developer"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "developer")
                     handler
                     read-ok-body)]
        (is (= 1 (count data)))
        (check-alice-entitlement (first data))))

    (testing "just for alice as an owner"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "owner")
                     handler
                     read-ok-body)]
        (is (= 1 (count data)))
        (check-alice-entitlement (first data))))

    (testing "also expired / ended as an owner"
      (let [data (-> (request :get "/api/entitlements?expired=true")
                     (authenticate api-key "owner")
                     handler
                     read-ok-body)]
        (is (= 3 (count data)))
        (check-alice-entitlement (first data))
        (check-alice-expired-entitlement (second data))
        (check-malice-entitlement (nth data 2))))

    (testing "just for alice as a reporter"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "reporter")
                     handler
                     read-ok-body)]
        (is (= 1 (count data)))
        (check-alice-entitlement (first data))))

    (testing "listing as applicant"
      (testing "with entitlements"
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "alice")
                       handler
                       assert-response-is-ok
                       read-ok-body)]
          (is (coll-is-not-empty? body))
          (doseq [x body]
            (check-alice-entitlement x))))

      (testing "without entitlements"
        (users/add-user! "allison" {})
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "allison")
                       handler
                       read-ok-body)]
          (is (coll-is-empty? body)))))))
