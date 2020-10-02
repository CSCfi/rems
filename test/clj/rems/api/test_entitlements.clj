(ns ^:integration rems.api.test-entitlements
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.api-key :as api-key]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture-without-data)

(deftest entitlements-test
  (testing "set up data"
    (api-key/add-api-key! 42 {})
    (users/add-user! {:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"})
    ;; don't set malice's email to test the api with nil emails
    (users/add-user! {:userid "malice" :name "Malice Applicant"})
    (users/add-user! {:userid "developer"})
    (users/add-user! {:userid "owner"})
    (roles/add-role! "owner" :owner)
    (users/add-user! {:userid "reporter"})
    (roles/add-role! "reporter" :reporter)
    (let [res-id (test-helpers/create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"})
          wf-id (test-helpers/create-workflow! {:type :workflow/default :handlers ["developer"]})
          form-id (test-helpers/create-form! {})
          cat-id (test-helpers/create-catalogue-item! {:resource-id res-id :form-id form-id :workflow-id wf-id})
          app-id (test-helpers/create-application! {:actor "alice" :catalogue-item-ids [cat-id]})
          expired-app-id (test-helpers/create-application! {:actor "alice" :catalogue-item-ids [cat-id]})
          malice-app-id (test-helpers/create-application! {:actor "malice" :catalogue-item-ids [cat-id]})]
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor "alice"})
      (test-helpers/command! {:type :application.command/approve
                              :application-id app-id
                              :actor "developer"})
      (test-helpers/command! {:type :application.command/submit
                              :application-id expired-app-id
                              :actor "alice"})
      (test-helpers/command! {:type :application.command/approve
                              :application-id expired-app-id
                              :actor "developer"})
      (test-helpers/command! {:type :application.command/close
                              :application-id expired-app-id
                              :actor "developer"})
      (test-helpers/command! {:type :application.command/submit
                              :application-id malice-app-id
                              :actor "malice"})
      (test-helpers/command! {:type :application.command/approve
                              :application-id malice-app-id
                              :actor "developer"
                              :entitlement-end (time/date-time 2100 01 01)})))

  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/entitlements"))
                       handler)
          body (read-body response)]
      (is (= "unauthorized" body))))
  (let [api-key "42"
        check-alice-entitlement (fn [x]
                                  (is (= {:user {:userid "alice"
                                                 :email "alice@example.com"
                                                 :name "Alice Applicant"
                                                 :nickname "In Wonderland"}
                                          :resource "urn:nbn:fi:lb-201403262"
                                          :end nil
                                          :mail "alice@example.com"}
                                         (dissoc x :start :application-id)))
                                  (is (valid-date? (:start x))))
        check-alice-expired-entitlement (fn [x]
                                          (is (= {:user {:userid "alice"
                                                         :email "alice@example.com"
                                                         :name "Alice Applicant"
                                                         :nickname "In Wonderland"}
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
                                           :end "2100-01-01T00:00:00.000Z"
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
        (users/add-user! {:userid "allison"})
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "allison")
                       handler
                       read-ok-body)]
          (is (coll-is-empty? body)))))))
