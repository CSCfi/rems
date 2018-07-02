(ns ^:integration rems.test.api.licenses
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

;; TODO license api test

(deftest licenses-api-security-test
  (testing "without authentication"
    (let [response (-> (request :get "/api/licenses")
                       app)]
      (is (= 401 (:status response)))))
  (testing "without owner role"
    (let [response (-> (request :get "/api/licenses")
                       (authenticate "42" "alice")
                       app)]
      (is (= 401 (:status response)))))
  (testing "with owner role"
    (let [body (-> (request :get "/api/licenses")
                   (authenticate "42" "owner")
                   app
                   read-body)]
      (is (string? (:licensetype (first body)))))))
