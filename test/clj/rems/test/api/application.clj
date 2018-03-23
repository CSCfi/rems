(ns ^:integration rems.test.api.application
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest application-api-test
  (let [api-key "42"
        user-id "alice"
        another-user "alice_smith"
        catid 2]
    (let [response (-> (request :put (str "/api/application/save"))
                       (authenticate api-key user-id)
                       (json {:command "save"
                              :catalogue-items [catid]
                              :items {1 "REST-Test"}})
                       app)
          cmd-response (read-body response)
          application-id (:id cmd-response)]
      (testing "saving"
        (is (:success cmd-response))
        (is (not (:errors cmd-response)))
        (is (= "draft" (:state cmd-response)))
        (is (not (:valid cmd-response)))
        (is (= ["Field \"Purpose of the project\" is required."
                "Field \"CC Attribution 4.0\" is required."
                "Field \"General Terms of Use\" is required."]
               (:validation cmd-response))))
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
      (testing "retrieving as other user"
        (let [response (-> (request :get (str "/api/application/" application-id))
                           (authenticate api-key another-user)
                           app)
              application (read-body response)]
          (is (= 401 (:status response)))))
      (testing "saving as other user"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key another-user)
                           (json {:command "save"
                                  :application-id application-id
                                  :items {1 "REST-Test"}})
                           app)]
          (is (= 401 (:status response)))))
      (testing "submitting"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json {:command "submit"
                                  :application-id application-id
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
          (is (= [nil "msg"] (map :comment (:events application)))))))))

(deftest application-validation-test
  (let [api-key "42"
        user-id "alice"
        catid 2]
    (let [response (-> (request :put (str "/api/application/save"))
                       (authenticate api-key user-id)
                       (json {:command "save"
                              :catalogue-items [catid]
                              ;; "" should fail validation just like nil
                              :items {1 ""}})
                       app)
          cmd-response (read-body response)
          validations (:validation cmd-response)
          application-id (:id cmd-response)]
      (testing "empty draft"
        (is (:success cmd-response))
        ;; 2 fields, 2 licenses
        (is (= 4 (count validations)))
        (is (some #(.contains % "Project name") validations))
        (is (some #(.contains % "Purpose of the project") validations))
        (is (some #(.contains % "CC Attribution 4.0") validations))
        (is (some #(.contains % "General Terms of Use") validations)))
      (testing "add one field"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json {:command "save"
                                  :application-id application-id
                                  :items {1 "FOO"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (:success cmd-response))
          (is (not (:valid cmd-response)))
          (is (= 3 (count validations)))))
      (testing "add one license"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json {:command "save"
                                  :application-id application-id
                                  :items {1 "FOO"}
                                  :licenses {2 "approved"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (:success cmd-response))
          (is (not (:valid cmd-response)))
          (is (= 2 (count validations)))))
      (testing "submit partial form"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json {:command "submit"
                                  :application-id application-id
                                  :items {1 "FOO"}
                                  :licenses {2 "approved"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (not (:success cmd-response)))
          (is (not (:valid cmd-response)))
          (is (= 2 (count validations)))))
      (testing "save full form"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json {:command "save"
                                  :application-id application-id
                                  :items {1 "FOO" 2 "ding" 3 "plong"}
                                  :licenses {2 "approved" 3 "approved"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (:success cmd-response))
          (is (:valid cmd-response))
          (is (empty? validations))))
      (testing "submit full form"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json {:command "submit"
                                  :application-id application-id
                                  :items {1 "FOO" 2 "ding" 3 "plong"}
                                  :licenses {2 "approved" 3 "approved"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (:success cmd-response))
          (is (:valid cmd-response))
          (is (empty? validations)))))))

(deftest disabled-catalogue-item
  (let [api-key "42"
        user-id "alice"
        catid 2]
    (testing "save draft for disabled item"
      (let [response (-> (request :put (str "/api/application/save"))
                         (authenticate api-key user-id)
                         (json {:command "save"
                                :catalogue-items [6]
                                :items {1 ""}})
                         app)
            cmd-response (read-body response)]
        ;; TODO should we actually return a nice error message here?
        (is (= 400 (:status response)))))
    (testing "submit for disabled item"
      (let [response (-> (request :put (str "/api/application/save"))
                         (authenticate api-key user-id)
                         (json {:command "submit"
                                :catalogue-items [6]
                                :items {1 "x" 2 "y" 3 "z"}
                                :licenses {2 "approved" 3 "approved"}})
                         app)
            cmd-response (read-body response)]
        (is (= 400 (:status response)))))))
