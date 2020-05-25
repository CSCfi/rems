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
        (let [response (-> (request :get "/api/catalogue")
                           handler)]
          (is (response-is-ok? response))))
      (testing "with authentification by api key"
        (let [response (-> (request :get "/api/catalogue")
                           (authenticate "42" "handler")
                           handler)]
          (is (response-is-ok? response))))
      (testing "with authentification by api key and username"
        (let [response (-> (request :get "/api/catalogue")
                           (authenticate "42" "alice")
                           handler)]
          (is (response-is-ok? response))))))
  (testing "catalogue-is-public false"
    (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public false)]
      (testing "should return forbidden without authentification"
        (let [response (-> (request :get "/api/catalogue")
                           handler)]
          (is (= "forbidden" (read-body response)))))
      (testing "with authentification should return catalogue pt.1"
        (let [response (-> (request :get "/api/catalogue")
                           (authenticate "42" "handler")
                           handler)]
          (is (response-is-ok? response))))
      (testing "with authentification should return catalogue pt.2"
        (let [response (-> (request :get "/api/catalogue")
                           (authenticate "42" "alice")
                           handler)]
          (is (response-is-ok? response))))
      (testing "with wrong api key should return forbidden"
        (let [response (-> (request :get (str "/api/catalogue"))
                           (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                           handler)]
          (is (= "forbidden" (read-body response))))))))
