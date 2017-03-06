(ns rems.test.handler
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [rems.handler :refer :all]))

(deftest test-app
  (testing "main route"
    (let [response (app (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "catalogue route"
    (let [response (app (request :get "/catalogue"))]
      (is (= 403 (:status response)))))

  (testing "CSRF forgery"
    (let [response (app (request :post "/Shibboleth.sso/Login"))]
      (is (= 403 (:status response)))
      (is (.contains (:body response) "anti-forgery"))))

  (testing "not-found route"
    (let [response (app (request :get "/invalid"))]
      (is (= 404 (:status response))))))
