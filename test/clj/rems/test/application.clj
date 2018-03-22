(ns ^:integration rems.test.application
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

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
            (is (= 3 (count (:items application))))))
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
