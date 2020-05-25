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
      (testing "with no authentification"
        (is (api-call :get "/api/catalogue" nil nil nil)))
      (testing "with authentification by api key"
        (is (api-call :get "/api/catalogue" nil "42" "handler")))
      (testing "with authentification by api key and username"
        (is (api-call :get "/api/catalogue" nil "42" "alice")))))
  (testing "catalogue-is-public false"
    (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public false)]
      (testing "should return forbidden without authentification"
        (is (= "forbidden" (read-body (api-response :get "/api/catalogue" nil nil nil)))))
      (testing "with authentification should return catalogue pt.1"
        (is (api-call :get "/api/catalogue" nil "42" "handler")))
      (testing "with authentification should return catalogue pt.2"
        (is (api-call :get "/api/catalogue" nil "42" "alice")))
      (testing "with wrong api key should return forbidden"
        (is (= "forbidden" (read-body (api-response :get "/api/catalogue" nil "invalid-api-key" nil))))))))
