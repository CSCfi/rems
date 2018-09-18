(ns ^:integration rems.test.api.catalogue
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]
            [clojure.string :as str]))

(use-fixtures
  :once
  api-fixture)

(deftest catalogue-api-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/catalogue/")
                   (authenticate api-key user-id)
                   app
                   read-body)
          item (first data)]
      (is (str/starts-with? (:resid item) "urn:")))))

(deftest catalogue-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/catalogue"))
                       app)
          body (read-body response)]
      (is (response-is-unauthorized? response))
      (is (= "unauthorized" body))))
  (testing "listing with wrong API-Key"
    (is (= "invalid api key"
           (-> (request :get (str "/api/catalogue"))
               (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
               app
               (read-body))))))
