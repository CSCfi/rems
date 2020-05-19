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
  (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public true)]
    (testing "catalogue-is-public true with no authentification"
      (let [response (-> (request :get "/api/catalogue")
                         handler)]
        (is (response-is-ok? response))))
    (let [api-key "42"]
      (testing "catalogue-is-public true with authentification by api key"
        (let [response (-> (request :get "/api/catalogue")
                           (authenticate api-key "handler")
                           handler)]
          (is (response-is-ok? response)))))
    (let [api-key "42"
          user-id "alice"]
      (testing "catalogue-is-public true with authentification by api key and username"
        (let [response (-> (request :get "/api/catalogue")
                           (authenticate api-key user-id)
                           handler)]
          (is (response-is-ok? response))))))
  (with-redefs [rems.config/env (assoc rems.config/env :catalogue-is-public false)]
    (testing "catalogue-is-public false should return forbidden without authentification"
      (let [response (-> (request :get "/api/catalogue")
                         handler)]
        (is (= "forbidden" (read-body response)))))
    (let [api-key "42"]
      (testing "catalogue-is-public false with authentification should return catalogue pt.1"
        (let [response (-> (request :get "/api/catalogue")
                           (authenticate api-key "handler")
                           handler)]
          (is (response-is-ok? response)))))
    (let [api-key "42"
          user-id "alice"]
      (testing "catalogue-is-public false with authentification should return catalogue pt.2"
        (let [response (-> (request :get "/api/catalogue")
                           (authenticate api-key user-id)
                           handler)]
          (is (response-is-ok? response)))))
    (testing "catalogue-is-public false with wrong api key should return forbidden"
      (let [response (-> (request :get (str "/api/catalogue"))
                         (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                         handler)]
        (is (= "forbidden" (read-body response)))))))



