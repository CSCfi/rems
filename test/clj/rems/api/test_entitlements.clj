(ns ^:integration rems.api.test-entitlements
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest entitlements-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/entitlements"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body)))))
