(ns ^:integration rems.api.test-permissions
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest permissions-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/permissions/userx"))
                       handler)
          body (read-body response)]
      (is (= "unauthorized" body))))

  (testing "all"
    (let [api-key "42"
          data (-> (request :get "/api/permissions/alice")
                   (authenticate api-key "developer")
                   handler
                   read-ok-body)
          result (first data)]
      (is (= 1 (count data)))
      (is (= {:ga4gh_visa_v1
              {:type     "ControlledAccessGrants",
               :value    "urn:nbn:fi:lb-201403262",
               :source   "https://ga4gh.org/duri/no_org",
               :by       "developer"}}
             (update-in result [:ga4gh_visa_v1] dissoc :asserted))))))  ;;TODO: add test for type of asserted


