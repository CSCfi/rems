(ns ^:integration rems.api.test-permissions
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(defn- validate-alice-result [data]
  (is (= 1 (count data)))
  (is (contains? data :ga4gh_visa_v1))
  (is (vector? (:ga4gh_visa_v1 data)))
  (is (string? (first (:ga4gh_visa_v1 data)))))

(deftest permissions-test
  (let [api-key "42"]
        (testing "listing without authentication"
          (let [response (-> (request :get (str "/api/permissions/userx"))
                             handler)
                body (read-body response)]
            (is (= "unauthorized" body))))

        (testing "listing without appropriate role"
          (let [response (-> (request :get (str "/api/permissions/alice"))
                             (authenticate "42" "approver1")
                             handler)
                body (read-body response)]
            (is (= {:ga4gh_visa_v1 []} body))))

        (testing "all for alice as handler"
          (let [data (-> (request :get "/api/permissions/alice")
                         (authenticate api-key "handler")
                         handler
                         read-ok-body)]
            (validate-alice-result data)))

        (testing "all for alice as owner"
          (let [data (-> (request :get "/api/permissions/alice")
                         (authenticate api-key "owner")
                         handler
                         read-ok-body)]
            (validate-alice-result data)))

        (testing "all for alice as organization-owner"
          (let [data (-> (request :get "/api/permissions/alice")
                         (authenticate api-key "organization-owner1")
                         handler
                         read-ok-body)]
            (validate-alice-result data)))

        (testing "all for alice as reporter"
          (let [data (-> (request :get "/api/permissions/alice")
                         (authenticate api-key "organization-owner1")
                         handler
                         read-ok-body)]
            (validate-alice-result data)))))



