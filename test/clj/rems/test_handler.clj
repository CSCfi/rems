(ns ^:integration rems.test-handler
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer [api-fixture-without-data read-ok-body]]
            [rems.common.git :as git]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

;; we shouldn't need the db here, but there is no handler-without-db
;; fixture at the moment
(use-fixtures :once api-fixture-without-data)

(deftest test-caching
  (with-redefs [git/+version+ {:version "0.0.0" :revision "abcd1234"}]
    (let [response (-> (request :get "/")
                       handler)
          body (read-ok-body response)]
      (testing "cache header for / resource"
        (is (= "no-store" (get-in response [:headers "Cache-Control"]))))
      (testing "cache-busting for app.js"
        (is (.contains body "app.js?abcd1234")))
      (testing "cache-busting for screen.css"
        (is (.contains body "screen.css?abcd1234")))))
  (testing "Cache-Control header for /redirect"
    (let [response (-> (request :get "/redirect")
                       handler)]
      (is (= 200 (:status response)))
      (is (= "no-store" (get-in response [:headers "Cache-Control"])))))
  (testing "Cache-Control header for /fake-login"
    (let [response (-> (request :get "/fake-login")
                       handler)]
      (is (= 200 (:status response)))
      (is (= "no-store" (get-in response [:headers "Cache-Control"])))))
  (testing "default Cache-Control header"
    (let [response (-> (request :get "/img/rems_logo_en.png")
                       handler)]
      (is (= 200 (:status response)))
      (is (= (str "max-age=" (* 60 60 23))
             (get-in response [:headers "Cache-Control"])))))
  (testing "api Cache-Control header"
    (let [response (-> (request :get "/api/health")
                       handler)]
      (is (= 200 (:status response)))
      (is (= "no-store" (get-in response [:headers "Cache-Control"]))))))
