(ns ^:integration rems.api.test-catalogue
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest catalogue-api-test
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (let [res (test-helpers/create-resource! {:resource-ext-id "urn:1234"})]
    (test-helpers/create-catalogue-item! {:actor "owner" :resource-id res}))
  (let [items (-> (request :get "/api/catalogue/")
                  (authenticate test-data/+test-api-key+ "alice")
                  handler
                  read-ok-body)]
    (is (= ["urn:1234"] (map :resid items)))))

(deftest catalogue-api-security-test
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (testing "catalogue-is-public true"
    (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public true)]
      (is (api-call :get "/api/catalogue" nil nil nil) "should work without authentication")
      (is (api-call :get "/api/catalogue" nil "42" nil) "should work with api key even without a user")
      (is (api-call :get "/api/catalogue" nil "42" "alice") "should work for a regular user")))
  (testing "catalogue-is-public false"
    (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public false)]
      (is (api-call :get "/api/catalogue" nil "42" "alice") "should work for a regular user")
      (is (= "unauthorized" (read-body (api-response :get "/api/catalogue" nil nil nil))) "should be unauthorized without authentication")
      (is (= "unauthorized" (read-body (api-response :get "/api/catalogue" nil "invalid-api-key" nil))) "should not work with wrong api key")
      (is (= "unauthorized" (read-body (api-response :get "/api/catalogue" nil "42" nil))) "should not work without a user"))))
