(ns ^:integration rems.api.test-entitlements
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.api-key :as api-key]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.testing-util :refer [utc-fixture with-fixed-time]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  utc-fixture
  api-fixture)

(def ^:private api-key "42")

(defn- check-alice-entitlement [data]
  (is (= {:user {:userid "alice"
                 :email "alice@example.com"
                 :name "Alice Applicant"
                 :nickname "In Wonderland"}
          :resource "urn:nbn:fi:lb-201403262"
          :end nil
          :mail "alice@example.com"}
         (dissoc data :start :application-id)))
  (is (valid-date? (:start data))))

(defn- check-alice-expired-entitlement [data]
  (is (= {:user {:userid "alice"
                 :email "alice@example.com"
                 :name "Alice Applicant"
                 :nickname "In Wonderland"}
          :resource "urn:nbn:fi:lb-201403262"
          :mail "alice@example.com"}
         (dissoc data :start :end :application-id)))
  (is (valid-date? (:start data)))
  (is (valid-date? (:end data))))

(defn- check-malice-entitlement [data]
  (is (= {:user {:userid "malice"
                 :email nil
                 :name "Malice Applicant"}
          :resource "urn:nbn:fi:lb-201403262"
          :end "2100-01-01T00:00:00.000Z"
          :mail nil}
         (dissoc data :start :application-id)))
  (is (valid-date? (:start data))))

(defn- read-ok-csv [response & [{:keys [separator]}]]
  (is (str/starts-with? (get-content-type response) "text/csv"))
  (->> (read-ok-body response)
       (str/split-lines)
       (mapv #(str/split % (re-pattern (or separator ","))))))

(deftest entitlements-test
  (with-fixed-time (time/date-time 2010)
    (fn []
      (api-key/add-api-key! api-key {})
      (test-helpers/create-user! {:userid "alice" :name "Alice Applicant" :email "alice@example.com" :nickname "In Wonderland"})
      ;; don't set malice's email to test the api with nil emails
      (test-helpers/create-user! {:userid "malice" :name "Malice Applicant"})
      (test-helpers/create-user! {:userid "handler"})
      ;; owner is automatically created by (test-helpers/create-resource!) but here for consistency
      (test-helpers/create-user! {:userid "owner"} :owner)
      (test-helpers/create-user! {:userid "reporter"} :reporter)
      (let [res-id (test-helpers/create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"})
            wf-id (test-helpers/create-workflow! {:type :workflow/default :handlers ["handler"]})
            form-id (test-helpers/create-form! {})
            cat-id (test-helpers/create-catalogue-item! {:resource-id res-id :form-id form-id :workflow-id wf-id})
            app-id (test-helpers/create-application! {:actor "alice" :catalogue-item-ids [cat-id]})
            expired-app-id (test-helpers/create-application! {:actor "alice" :catalogue-item-ids [cat-id]})
            malice-app-id (test-helpers/create-application! {:actor "malice" :catalogue-item-ids [cat-id]})]
        (testing "set up data"
          (test-helpers/command! {:type :application.command/submit
                                  :application-id app-id
                                  :actor "alice"})
          (test-helpers/command! {:type :application.command/approve
                                  :application-id app-id
                                  :actor "handler"})
          (test-helpers/command! {:type :application.command/submit
                                  :application-id expired-app-id
                                  :actor "alice"})
          (test-helpers/command! {:type :application.command/approve
                                  :application-id expired-app-id
                                  :actor "handler"})
          (test-helpers/command! {:type :application.command/close
                                  :application-id expired-app-id
                                  :actor "handler"})
          (test-helpers/command! {:type :application.command/submit
                                  :application-id malice-app-id
                                  :actor "malice"})
          (test-helpers/command! {:type :application.command/approve
                                  :application-id malice-app-id
                                  :actor "handler"
                                  :entitlement-end (time/date-time 2100 01 01)}))
        (testing "listing without authentication"
          (doseq [endpoint ["/api/entitlements" "/api/entitlements/export-csv"]]
            (testing endpoint
              (is (response-is-unauthorized? (api-response :get endpoint))))))

        (testing "all"
          (let [data (api-call :get "/api/entitlements" nil api-key "handler")]
            (is (= 2 (count data)))
            (check-alice-entitlement (first data))
            (check-malice-entitlement (second data))))

        (doseq [userid ["handler" "owner" "reporter"]]
          (testing (str "all as " userid)
            (let [data (api-call :get "/api/entitlements" nil api-key userid)]
              (is (= 2 (count data)))
              (check-alice-entitlement (first data))
              (check-malice-entitlement (second data)))))

        (testing "for one resource"
          (let [data (api-call :get "/api/entitlements?resource=urn:nbn:fi:lb-201403262" nil api-key "handler")]
            (is (= 2 (count data)))
            (check-alice-entitlement (first data))
            (check-malice-entitlement (second data))))

        (testing "just for alice as a handler"
          (let [data (api-call :get "/api/entitlements?user=alice" nil api-key "handler")]
            (is (= 1 (count data)))
            (check-alice-entitlement (first data))))

        (testing "just for alice as an owner"
          (let [data (api-call :get "/api/entitlements?user=alice" nil api-key "owner")]
            (is (= 1 (count data)))
            (check-alice-entitlement (first data))))

        (testing "also expired / ended as an owner"
          (let [data (api-call :get "/api/entitlements?expired=true" nil api-key "owner")]
            (is (= 3 (count data)))
            (check-alice-entitlement (first data))
            (check-alice-expired-entitlement (second data))
            (check-malice-entitlement (nth data 2))))

        (testing "just for alice as a reporter"
          (let [data (api-call :get "/api/entitlements?user=alice" nil api-key "reporter")]
            (is (= 1 (count data)))
            (check-alice-entitlement (first data))))

        (testing "listing as applicant"
          (testing "with entitlements"
            (let [data (api-call :get "/api/entitlements" nil api-key "alice")]
              (is (coll-is-not-empty? data))
              (doseq [x data]
                (check-alice-entitlement x))))

          (testing "without entitlements"
            (test-helpers/create-user! {:userid "allison"})
            (is (coll-is-empty?
                 (api-call :get "/api/entitlements" nil api-key "allison")))))

        (testing "export entitlements csv"
          (let [api "/api/entitlements/export-csv"]
            (doseq [user #{"handler" "reporter"}
                    :let [get-entitlements-csv (fn [& {:keys [separator] :as query-params}]
                                                 (-> (api-response :get api nil api-key user query-params)
                                                     (read-ok-csv {:separator (or separator ",")})))]]
              (testing (str "as " user)
                (is (= [["resource" "application" "user" "start"]
                        ["urn:nbn:fi:lb-201403262" (str app-id) "alice" "2010-01-01T00:00:00.000Z"]
                        ["urn:nbn:fi:lb-201403262" (str expired-app-id) "alice" "2010-01-01T00:00:00.000Z"]
                        ["urn:nbn:fi:lb-201403262" (str malice-app-id) "malice" "2010-01-01T00:00:00.000Z"]]
                       (get-entitlements-csv {:separator ";" :resource "urn:nbn:fi:lb-201403262"})
                       (get-entitlements-csv))) ; backwards compatibility
                (is (= [["resource" "application" "user" "start"]
                        ["urn:nbn:fi:lb-201403262" (str app-id) "alice" "2010-01-01T00:00:00.000Z"]]
                       (get-entitlements-csv {:separator "\t" :user "alice" :expired false})))
                (is (= [["resource" "application" "user" "start"]
                        ["urn:nbn:fi:lb-201403262" (str malice-app-id) "malice" "2010-01-01T00:00:00.000Z"]]
                       (get-entitlements-csv {:user "malice"})))))
            (testing "without proper role"
              (doseq [user ["alice" "malice" "owner"]]
                (testing (str "as " user)
                  (is (response-is-forbidden?
                       (api-response :get api nil api-key user))))))
            (testing "redirect /entitlements.csv"
              (let [response (-> (api-response :get "/entitlements.csv" nil api-key "handler")
                                 (assert-response-is-redirect))]
                (is (-> (get-redirect-location response)
                        (str/ends-with? api)))))))))))

