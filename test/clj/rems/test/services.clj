(ns rems.test.services
  (:require [cheshire.core :refer [generate-string parse-stream]]
            [clojure.java.io]
            [clojure.test :refer :all]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

(defn authenticate [request api-key user-id]
  (-> request
      (assoc-in [:headers "x-rems-api-key"] api-key)
      (assoc-in [:headers "x-rems-user-id"] user-id)))

(defn json [request m]
  (-> request
      (content-type "application/json")
      (body (generate-string m))))

(defn read-body [response]
  (parse-stream (clojure.java.io/reader (:body response)) true))

(deftest service-application-test
  (let [api-key "42"
        user-id "alice"]
    (testing "saving application"
      (let [response (-> (request :put "/api/application/2")
                         (authenticate api-key user-id)
                         (json {:operation "save"
                                :fields {2 "ensimmäinen"}})
                         app)
            cmd-response (read-body response)
            application-id (:id cmd-response)]
        (is (:success cmd-response))
        (is (= :draft (:state cmd-response)))
        (is (not (:valid cmd-response)))
        (is (= ["Field \"Additional Information\" is required."]
               (:validation cmd-response)))
        ))
    (testing "retrieving application"
      (let [response (-> (request :get "/api/application/2")
                         (authenticate api-key user-id)
                         app)
            application (read-body response)]
        (is (= 2 (:id application)))
        (is (= :draft (:state application)))
        (is (empty? (:licenses application)))
        (is (= 2 (count (:items application))))
        ))
    (testing "sending application"
      (let [response (-> (request :put "/api/application/2")
                         (authenticate api-key user-id)
                         (json {:operation "send"
                                :fields {2 "ensimmäinen"
                                         8 "second"}})
                         app)
            cmd-response (read-body response)]
        (is (= :applied (:state cmd-response)))
        (is (:success cmd-response))
        (is (:valid cmd-response))
        ))))
