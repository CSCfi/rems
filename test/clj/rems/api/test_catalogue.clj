(ns ^:integration rems.api.test-catalogue
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest catalogue-api-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/catalogue/")
                   (authenticate api-key user-id)
                   handler
                   read-ok-body)]
      (is (some #(str/starts-with? (:resid %) "urn:") data)))))

(deftest catalogue-api-security-test
  (testing "catalogue-is-public true"
    (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public true)]
      (is (api-call :get "/api/catalogue" nil nil nil) "should work without authentication")
      (is (api-call :get "/api/catalogue" nil "42" nil) "should work with api key even without a user")
      (is (api-call :get "/api/catalogue" nil "42" "alice") "should work for a regular user")))
  (testing "catalogue-is-public false"
    (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public false)]
      (is (api-call :get "/api/catalogue" nil "42" "alice") "should work for a regular user")
      (is (= "forbidden" (read-body (api-response :get "/api/catalogue" nil nil nil))) "should be forbidden without authentication")
      (is (= "forbidden" (read-body (api-response :get "/api/catalogue" nil "invalid-api-key" nil))) "should not work with wrong api key")
      (is (= "forbidden" (read-body (api-response :get "/api/catalogue" nil "42" nil))) "should not work without a user"))))
