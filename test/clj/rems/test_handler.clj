(ns rems.test-handler
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer [read-ok-body]]
            [rems.common.git :as git]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

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
        (is (.contains body "screen.css?abcd1234"))))))
