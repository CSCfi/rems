(ns ^:integration rems.test.services
  (:require [cheshire.core :refer [generate-string parse-stream]]
            [clojure.string :refer [starts-with?]]
            [clojure.test :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.handler :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  (fn [f]
    (mount/start
     #'rems.config/env
     #'rems.env/*db*
     #'rems.handler/app)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (test-data/create-test-data!)
    (f)
    (mount/stop)))

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
        user-id "alice"
        catid 2]
    (testing "saving"
      (let [response (-> (request :put (str "/api/application/save"))
                         (authenticate api-key user-id)
                         (json {:command "save"
                                :catalogue-items [catid]
                                :items {1 "REST-Test"}})
                         app)
            cmd-response (read-body response)
            application-id (:id cmd-response)]
        (is (:success cmd-response))
        (is (not (:errors cmd-response)))
        (is (= "draft" (:state cmd-response)))
        (is (not (:valid cmd-response)))
        (is (= ["Field \"Purpose of the project\" is required."
                "Field \"CC Attribution 4.0\" is required."
                "Field \"General Terms of Use\" is required."]
               (:validation cmd-response)))
        (testing "retrieving"
          (let [response (-> (request :get (str "/api/application/" application-id))
                             (authenticate api-key user-id)
                             app)
                application (read-body response)]
            (is (not (:errors application)))
            (is (= application-id (:id (:application application))))
            (is (= "draft" (:state (:application application))))
            (is (= 2 (count (:licenses application))))
            (is (= 3 (count (:items application))))
            ))
        (testing "submitting"
          (let [response (-> (request :put (str "/api/application/save"))
                             (authenticate api-key user-id)
                             (json {:command "submit"
                                    :application-id application-id
                                    :catalogue-items [catid]
                                    :items {1 "REST-Test"
                                            2 "2017-2018"
                                            3 "The purpose is to test this REST service.}"}
                                    :licenses {2 "approved" 3 "approved"}})
                             app)
                cmd-response (read-body response)]
            (is (:success cmd-response))
            (is (not (:errors cmd-response)))
            (is (= application-id (:id cmd-response)))
            (is (= "applied" (:state cmd-response)))
            (is (:valid cmd-response))
            (is (empty? (:validation cmd-response)))))
        (testing "approving"
          (let [response (-> (request :put (str "/api/application/judge"))
                             (authenticate api-key "developer")
                             (json {:command "approve"
                                    :application-id application-id
                                    :round 0
                                    :comment "msg"})
                             app)
                cmd-response (read-body response)
                application-response (-> (request :get (str "/api/application/" application-id))
                                         (authenticate api-key user-id)
                                         app)
                application (:application (read-body application-response))]
            (is (:success cmd-response))
            (is (= "approved" (:state application)))
            (is (= [nil "msg"] (map :comment (:events application))))))))))

(deftest service-catalogue-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/catalogue/")
                   (authenticate api-key user-id)
                   app
                   read-body)
          item (first data)]
      (is (starts-with? (:resid item) "http://")))))
