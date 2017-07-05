(ns rems.test.services
  (:require [cheshire.core :refer [generate-string parse-stream]]
            [clojure.java.io]
            [clojure.test :refer :all]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

(deftest form-service-test
  (let [api-key "42"
        user-id "alice"]
    (testing "retrieving form"
      (let [response (-> (request :get "/api/form/2")
                         (assoc-in [:headers "x-rems-api-key"] api-key)
                         (assoc-in [:headers "x-rems-user-id"] user-id)
                         app)
            form (parse-stream (clojure.java.io/reader (:body response)) true)]
        (is (= 2 (:id form)))
        (is (empty? (:licenses form)))
        (is (= 2 (count (:items form))))))
    (testing "sending form"
      (let [response (-> (request :put "/api/form/2")
                         (assoc-in [:headers "x-rems-api-key"] api-key)
                         (assoc-in [:headers "x-rems-user-id"] user-id)
                         (content-type "application/json")
                         (body (generate-string {:operation "send"
                                                 :fields []}))
                         app)
            cmd-response (parse-stream (clojure.java.io/reader (:body response)) true)]
        (is (:success cmd-response)))
        )))
