(ns ^:integration rems.api.test-entitlements
  (:require [clojure.test :refer :all]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures :once api-once-fixture)
(use-fixtures :each api-each-fixture)

(deftest entitlements-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/entitlements"))
                       handler)
          body (read-body response)]
      (is (= "unauthorized" body)))))
