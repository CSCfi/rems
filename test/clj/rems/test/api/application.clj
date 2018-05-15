(ns ^:integration rems.test.api.application
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
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
                       (json-body {:command "save"
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
        (is (= [{:type "item"
                 :id 2
                 :title {:en "Purpose of the project" :fi "Projektin tarkoitus"}
                 :key "t.form.validation/required"
                 :text "Field \"Purpose of the project\" is required."}
                {:type "license"
                 :id 1
                 :title {:en "CC Attribution 4.0" :fi "CC Nimeä 4.0"}
                 :key "t.form.validation/required"
                 :text "Field \"non-localized link license\" is required."}
                {:type "license"
                 :id 2
                 :title {:en "General Terms of Use", :fi "Yleiset käyttöehdot"}
                 :key "t.form.validation/required"
                 :text "Field \"non-localized text license\" is required."}]
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
                           (json-body {:command "save"
                                       :application-id application-id
                                       :items {1 "REST-Test"}})
                           app)]
          (is (= 401 (:status response)))))
      (testing "submitting"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json-body {:command "submit"
                                       :application-id application-id
                                       :items {1 "REST-Test"
                                               2 "2017-2018"
                                               3 "The purpose is to test this REST service.}"}
                                       :licenses {1 "approved" 2 "approved"}})
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
                           (json-body {:command "approve"
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
                       (json-body {:command "save"
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
        (is (some #(.contains (:text %) "Project name") validations))
        (is (some #(.contains (:text %) "Purpose of the project") validations))
        (is (some #(.contains (:text %) "non-localized link license") validations))
        (is (some #(.contains (:text %) "non-localized text license") validations)))
      (testing "add one field"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json-body {:command "save"
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
                           (json-body {:command "save"
                                       :application-id application-id
                                       :items {1 "FOO"}
                                       :licenses {1 "approved"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (:success cmd-response))
          (is (not (:valid cmd-response)))
          (is (= 2 (count validations)))))
      (testing "submit partial form"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json-body {:command "submit"
                                       :application-id application-id
                                       :items {1 "FOO"}
                                       :licenses {1 "approved"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (not (:success cmd-response)))
          (is (not (:valid cmd-response)))
          (is (= 2 (count validations)))))
      (testing "save full form"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json-body {:command "save"
                                       :application-id application-id
                                       :items {1 "FOO" 2 "ding" 3 "plong"}
                                       :licenses {1 "approved" 2 "approved"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (:success cmd-response))
          (is (:valid cmd-response))
          (is (empty? validations))))
      (testing "submit full form"
        (let [response (-> (request :put (str "/api/application/save"))
                           (authenticate api-key user-id)
                           (json-body {:command "submit"
                                       :application-id application-id
                                       :items {1 "FOO" 2 "ding" 3 "plong"}
                                       :licenses {1 "approved" 2 "approved"}})
                           app)
              cmd-response (read-body response)
              validations (:validation cmd-response)]
          (is (:success cmd-response))
          (is (:valid cmd-response))
          (is (empty? validations)))))))

(deftest disabled-catalogue-item
  (let [api-key "42"
        user-id "developer"
        catid 6]
    (testing "save draft for disabled item"
      (let [response (-> (request :put (str "/api/application/save"))
                         (authenticate api-key user-id)
                         (json-body {:command "save"
                                     :catalogue-items [catid]
                                     :items {1 ""}})
                         app)
            cmd-response (read-body response)]
        ;; TODO should we actually return a nice error message here?
        (is (= 400 (:status response)) "should not be able to save draft with disbled item")))
    (testing "submit for application with disabled item"
      (let [response (-> (request :put (str "/api/application/save"))
                         (authenticate api-key user-id)
                         (json-body {:application-id 6 ;; application-id 6 is already created, but catalogue-item was disabled later
                                     :command "submit"
                                     :catalogue-items [catid]
                                     :items {1 "x" 2 "y" 3 "z"}
                                     :licenses {1 "approved" 2 "approved"}})
                         app)
            cmd-response (read-body response)]
        (is (= 400 (:status response)) "should not be possible to submit with disabled item")))))

(deftest application-api-roles
  (let [api-key "42"
        applicant "alice"
        approver "developer"
        catid 2]
    (let [response (-> (request :put (str "/api/application/save"))
                       (authenticate api-key applicant)
                       (json-body {:command "submit"
                                   :catalogue-items [catid]
                                   :items {1 "x" 2 "y" 3 "z"}
                                   :licenses {1 "approved" 2 "approved"}})
                       app)
          cmd-response (read-body response)
          app-id (:id cmd-response)]
      (is (number? app-id))
      (testing "get application as applicant"
        (let [application (-> (request :get (str "/api/application/" app-id))
                              (authenticate api-key applicant)
                              app
                              read-body
                              :application)]
          (is (:can-withdraw? application))
          (is (:can-close? application))
          (is (not (:can-approve? application)))))
      (testing "get application as approver"
        (let [application (-> (request :get (str "/api/application/" app-id))
                              (authenticate api-key approver)
                              app
                              read-body
                              :application)]
          (is (not (:can-close? application)))
          (is (:can-approve? application))))
      ;; TODO tests for :review-type
      (testing "approve application"
        (is (= 200 (-> (request :put (str "/api/application/judge"))
                       (authenticate api-key approver)
                       (json-body {:command "approve"
                                   :application-id app-id
                                   :round 0
                                   :comment "msg"})
                       app
                       :status))))
      (testing "get approved application as applicant"
        (let [application (-> (request :get (str "/api/application/" app-id))
                              (authenticate api-key applicant)
                              app
                              read-body
                              :application)]
          (is (:can-close? application))
          (is (not (:can-approve? application)))))
      (testing "get approved application as approver"
        (let [application (-> (request :get (str "/api/application/" app-id))
                              (authenticate api-key approver)
                              app
                              read-body
                              :application)]
          (is (:can-close? application))
          (is (not (:can-approve? application))))))))

(deftest application-api-action-test
  ;; Run through all the application actions that are available
  (let [api-key "42"
        user "developer"
        catid 2
        app-id (-> (request :put (str "/api/application/save"))
                   (authenticate api-key user)
                   (json-body {:command "save"
                               :catalogue-items [catid]
                               :items {1 "x" 2 "y" 3 "z"}
                               :licenses {1 "approved" 2 "approved"}})
                   app
                   read-body
                   :id)
        submit (fn []
                 (is (= 200
                        (-> (request :put (str "/api/application/save"))
                            (authenticate api-key user)
                            (json-body {:command "submit"
                                        :application-id app-id
                                        :items {1 "x" 2 "y" 3 "z"}
                                        :licenses {1 "approved" 2 "approved"}})
                            app
                            :status))))
        action (fn [body]
                (is (= 200
                       (-> (request :put (str "/api/application/judge"))
                           (authenticate api-key user)
                           (json-body (merge {:application-id app-id
                                              :round 0}
                                             body))
                           app
                           :status))))]
    (submit)
    (action {:command "return"
             :comment "returned"})
    (submit)
    (action {:command "withdraw"
             :comment "withdrawn"})
    (submit)
    (action {:command "approve"
             :comment "approved"})
    (action {:command "close"
             :comment "closed"})
    (let [events (-> (request :get (str "/api/application/" app-id))
                     (authenticate api-key user)
                     app
                     read-body
                     :application
                     :events)]
      (is (= [["apply" nil]
              ["return" "returned"]
              ["apply" nil]
              ["withdraw" "withdrawn"]
              ["apply" nil]
              ["approve" "approved"]
              ["close" "closed"]]
             (map (juxt :event :comment) events))))))

(deftest application-api-3rd-party-review-test
  (let [api-key "42"
        user "developer"
        reviewer "alice"
        catid 2
        app-id (-> (request :put (str "/api/application/save"))
                   (authenticate api-key user)
                   (json-body {:command "submit"
                               :catalogue-items [catid]
                               :items {1 "x" 2 "y" 3 "z"}
                               :licenses {1 "approved" 2 "approved"}})
                   app
                   read-body
                   :id)]
    (testing "send review request"
      (is (= 200
             (-> (request :put (str "/api/application/review_request"))
                 (authenticate api-key user)
                 (json-body {:application-id app-id
                             :round 0
                             :comment "pls revu"
                             :recipients [reviewer]})
                 app
                 :status))))
    (testing "check review event"
      (let [events (-> (request :get (str "/api/application/" app-id))
                     (authenticate api-key user)
                     app
                     read-body
                     :application
                     :events)]
        (is (= [{:userid "developer" :comment nil :event "apply"}
                {:userid "alice" :comment "pls revu" :event "review-request"}]
               (map #(select-keys % [:userid :comment :event]) events)))))
    (testing "send review"
      (is (= 200
             (-> (request :put (str "/api/application/judge"))
                 (authenticate api-key reviewer)
                 (json-body {:command "3rd-party-review"
                             :application-id app-id
                             :round 0
                             :comment "is ok"})
                 app
                 :status))))
    (testing "check events"
      (let [events (-> (request :get (str "/api/application/" app-id))
                     (authenticate api-key user)
                     app
                     read-body
                     :application
                     :events)]
        (is (= [{:userid "developer" :comment nil :event "apply"}
                {:userid "alice" :comment "pls revu" :event "review-request"}
                {:userid "alice" :comment "is ok" :event "third-party-review"}]
               (map #(select-keys % [:userid :comment :event]) events)))))))
;; TODO non-happy path tests for review?

;; TODO test for event filtering when it gets implemented

(deftest application-api-security-test
  (testing "save without authentication"
    (let [response (-> (request :put (str "/api/application/save"))
                       (json-body {:command "save"
                                   :catalogue-items [2]
                                   :items {1 "REST-Test"}})
                       app)
          body (read-body response)]
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "save with wrong API-Key"
    (let [response (-> (request :put (str "/api/application/save"))
                       (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                       (json-body {:command "save"
                                   :catalogue-items [2]
                                   :items {1 "REST-Test"}})
                       app)
          body (read-body response)]
      (is (= "unauthorized" body))))
  (testing "judge without authentication"
    (let [body (-> (request :put (str "/api/application/judge"))
                   (json-body {:command "approve"
                               :application-id 2
                               :round 0
                               :comment "msg"})
                   app
                   read-body)]
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "judge with wrong API-Key"
    (let [body (-> (request :put (str "/api/application/judge"))
                   (authenticate "invalid-api-key" "developer")
                   (json-body {:command "approve"
                               :application-id 2
                               :round 0
                               :comment "msg"})
                   app
                   read-body)]
      (is (= "unauthorized" body)))))
