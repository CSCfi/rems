(ns ^:integration rems.api.test-organizations
  (:require [clojure.test :refer :all]
            [medley.core :refer [find-first]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all])
  (:import [org.joda.time DateTime DateTimeZone DateTimeUtils]))

(def test-time (DateTime. 90000 DateTimeZone/UTC))

(use-fixtures
  :once
  api-fixture
  (fn [f]
    (DateTimeUtils/setCurrentMillisFixed (.getMillis test-time))
    (f)
    (DateTimeUtils/setCurrentMillisSystem)))

(deftest organizations-api-test
  (let [api-key "42"
        owner "owner"
        org-owner "organization-owner1"]

    (testing "finds test data"
      (let [data (api-call :get "/api/organizations"
                           nil
                           api-key owner)]
        (is (= #{"default" "perf" "hus" "thl" "csc" "nbn" "organization1" "organization2"}
               (set (map :organization/id data))))))

    (testing "create organization"
      (let [data (api-call :post "/api/organizations/create"
                           {:organization/id "organizations-api-test-org"
                            :organization/name "Organizations API Test ORG"
                            :organization/owners [{:userid org-owner}]
                            :organization/review-emails [{:email "test@organization.test.org"
                                                          :name "Organizations API Test ORG Reviewers"}]}
                           api-key owner)]
        (is (= "organizations-api-test-org" (:organization/id data)))

        (testing "organization owner can see it"
          (let [data (api-call :get (str "/api/organizations")
                               nil
                               api-key org-owner)]
            (is (contains? (set (map :organization/id data)) "organizations-api-test-org"))
            (is (= {:organization/id "organizations-api-test-org"
                    :organization/name "Organizations API Test ORG"
                    :organization/owners [{:userid org-owner}]
                    :organization/last-modified test-time
                    :organization/modifier {:userid owner}
                    :organization/review-emails [{:email "test@organization.test.org"
                                                  :name "Organizations API Test ORG Reviewers"}]}
                   (-> (find-first (comp #{"organizations-api-test-org"} :organization/id) data)
                       (update :organization/last-modified parse-date))))))

        (testing "organization owner owns it"
          (let [data (api-call :get (str "/api/organizations?owner=" org-owner)
                               nil
                               api-key org-owner)]
            (is (contains? (set (map :organization/id data)) "organizations-api-test-org"))))))))

(deftest organizations-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (api-response :get "/api/organizations")]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))

    (testing "create"
      (let [response (api-response :post "/api/organizations/create"
                                   {:organization/id "test-organization"
                                    :organization/name "Test Organization"
                                    :organization/owners []
                                    :organization/review-emails []})]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (api-call :get "/api/organizations"
                               nil
                               "42" "alice")]
        (is (not-any? #{:organization/owners :organization/review-emails}
                      (mapcat keys response))
            "can't see all the attributes")))

    (testing "create"
      (let [response (api-response :post "/api/organizations/create"
                                   {:organization/id "test-organization"
                                    :organization/name "Test Organization"
                                    :organization/owners []
                                    :organization/review-emails []}
                                   "42" "alice")]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
