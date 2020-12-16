(ns rems.test-handler
  (:require [clojure.test :refer :all]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

(deftest test-cache-headers
  (let [response (-> (request :get "/")
                     handler)]
    (is (= "no-store" (get-in response [:headers "Cache-Control"])))))
