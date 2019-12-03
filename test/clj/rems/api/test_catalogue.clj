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
                   read-body)
          item (first data)]
      (is (str/starts-with? (:resid item) "urn:")))))

(deftest catalogue-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/catalogue"))
                       handler)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (= "unauthorized" body))))
  (testing "listing with wrong API-Key"
    (is (= "unauthorized"
           (-> (request :get (str "/api/catalogue"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               handler
               (read-body))))))
