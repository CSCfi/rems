(ns ^:integration rems.test.api.applications
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest applications-api-test
  (testing "fetch applications"
    (let [api-key "42"
          user-id "developer"]
      (testing "regular fetch"
        (let [response (-> (request :get "/api/applications")
                           (authenticate api-key user-id)
                           (header "Accept" "application/json")
                           app
                           assert-response-is-ok)
              data (read-body response)]
          (is (= "application/json; charset=utf-8" (get-in response [:headers "Content-Type"])))
          (is (= [1 2 3 4 5 6 7] (map :id (sort-by :id data))))))
      (testing "transit support"
        (let [response (-> (request :get "/api/applications")
                           (authenticate api-key user-id)
                           (header "Accept" "application/transit+json")
                           app
                           assert-response-is-ok)
              data (read-body response)]
          (is (= "application/transit+json; charset=utf-8" (get-in response [:headers "Content-Type"])))
          (is (= 7 (count data))))))))

(deftest applications-api-command-test
  (let [api-key "42"
        user-id "alice"
        another-user "alice_smith"
        catid 2]
    (let [response (-> (request :post (str "/api/applications/save"))
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
        (is (= [{:type "t.form.validation/required" :field-id 2}
                {:type "t.form.validation/required" :license-id 1}
                {:type "t.form.validation/required" :license-id 2}]
               (:validation cmd-response))))
      (testing "retrieving"
        (let [response (-> (request :get (str "/api/applications/" application-id))
                           (authenticate api-key user-id)
                           app)
              application (read-body response)]
          (is (not (:errors application)))
          (is (= application-id (:id (:application application))))
          (is (= "draft" (:state (:application application))))
          (is (= 2 (count (:licenses application))))
          (is (< 6 (count (:items application))))))
      (testing "retrieving as other user"
        (let [response (-> (request :get (str "/api/applications/" application-id))
                           (authenticate api-key another-user)
                           app)]
          (is (response-is-forbidden? response))))
      (testing "saving as other user"
        (let [response (-> (request :post (str "/api/applications/save"))
                           (authenticate api-key another-user)
                           (json-body {:command "save"
                                       :application-id application-id
                                       :items {1 "REST-Test"}})
                           app)]
          (is (response-is-forbidden? response))))
      (testing "submitting"
        (let [response (-> (request :post (str "/api/applications/save"))
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
        (let [response (-> (request :post (str "/api/applications/judge"))
                           (authenticate api-key "developer")
                           (json-body {:command "approve"
                                       :application-id application-id
                                       :round 0
                                       :comment "msg"})
                           app)
              cmd-response (read-body response)
              application-response (-> (request :get (str "/api/applications/" application-id))
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
    (let [response (-> (request :post (str "/api/applications/save"))
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
        (is (= [{:type "t.form.validation/required" :field-id 1}
                {:type "t.form.validation/required" :field-id 2}
                {:type "t.form.validation/required" :license-id 1}
                {:type "t.form.validation/required" :license-id 2}]
               validations)))
      (testing "add one field"
        (let [response (-> (request :post (str "/api/applications/save"))
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
        (let [response (-> (request :post (str "/api/applications/save"))
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
        (let [response (-> (request :post (str "/api/applications/save"))
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
        (let [response (-> (request :post (str "/api/applications/save"))
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
        (let [response (-> (request :post (str "/api/applications/save"))
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
      (let [response (-> (request :post (str "/api/applications/save"))
                         (authenticate api-key user-id)
                         (json-body {:command "save"
                                     :catalogue-items [catid]
                                     :items {1 ""}})
                         app)]
        ;; TODO should we actually return a nice error message here?
        (is (= 400 (:status response)) "should not be able to save draft with disbled item")))
    (testing "submit for application with disabled item"
      (let [response (-> (request :post (str "/api/applications/save"))
                         (authenticate api-key user-id)
                         (json-body {:application-id 6 ;; application-id 6 is already created, but catalogue-item was disabled later
                                     :command "submit"
                                     :catalogue-items [catid]
                                     :items {1 "x" 2 "y" 3 "z"}
                                     :licenses {1 "approved" 2 "approved"}})
                         app)]
        (is (= 400 (:status response)) "should not be possible to submit with disabled item")))))

(deftest application-api-roles
  (let [api-key "42"
        applicant "alice"
        approver "developer"
        catid 2]
    (let [response (-> (request :post (str "/api/applications/save"))
                       (authenticate api-key applicant)
                       (json-body {:command "submit"
                                   :catalogue-items [catid]
                                   :items {1 "x" 2 "y" 3 "z"}
                                   :licenses {1 "approved" 2 "approved"}})
                       app
                       assert-response-is-ok)
          cmd-response (read-body response)
          app-id (:id cmd-response)]
      (is (number? app-id))
      (testing "get application as applicant"
        (let [application (-> (request :get (str "/api/applications/" app-id))
                              (authenticate api-key applicant)
                              app
                              read-body
                              :application)]
          (is (:can-withdraw? application))
          (is (:can-close? application))
          (is (not (:can-approve? application)))))
      (testing "get application as approver"
        (let [application (-> (request :get (str "/api/applications/" app-id))
                              (authenticate api-key approver)
                              app
                              read-body
                              :application)]
          (is (not (:can-close? application)))
          (is (:can-approve? application))))
      ;; TODO tests for :review-type
      (testing "approve application"
        (-> (request :post (str "/api/applications/judge"))
            (authenticate api-key approver)
            (json-body {:command "approve"
                        :application-id app-id
                        :round 0
                        :comment "msg"})
            app
            assert-response-is-ok))
      (testing "get approved application as applicant"
        (let [application (-> (request :get (str "/api/applications/" app-id))
                              (authenticate api-key applicant)
                              app
                              read-body
                              :application)]
          (is (:can-close? application))
          (is (not (:can-approve? application)))))
      (testing "get approved application as approver"
        (let [application (-> (request :get (str "/api/applications/" app-id))
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
        app-id (-> (request :post (str "/api/applications/save"))
                   (authenticate api-key user)
                   (json-body {:command "save"
                               :catalogue-items [catid]
                               :items {1 "x" 2 "y" 3 "z"}
                               :licenses {1 "approved" 2 "approved"}})
                   app
                   read-body
                   :id)
        submit (fn []
                 (-> (request :post (str "/api/applications/save"))
                     (authenticate api-key user)
                     (json-body {:command "submit"
                                 :application-id app-id
                                 :items {1 "x" 2 "y" 3 "z"}
                                 :licenses {1 "approved" 2 "approved"}})
                     app
                     assert-response-is-ok))
        action (fn [body]
                 (-> (request :post (str "/api/applications/judge"))
                     (authenticate api-key user)
                     (json-body (merge {:application-id app-id
                                        :round 0}
                                       body))
                     app
                     assert-response-is-ok))]
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
    (let [response (-> (request :get (str "/api/applications/" app-id))
                       (authenticate api-key user)
                       app
                       assert-response-is-ok)
          events (-> response
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

(deftest application-api-third-party-review-test
  (let [api-key "42"
        applicant "alice"
        approver "developer"
        reviewer "carl"
        catid 2
        app-id (-> (request :post (str "/api/applications/save"))
                   (authenticate api-key applicant)
                   (json-body {:command "submit"
                               :catalogue-items [catid]
                               :items {1 "x" 2 "y" 3 "z"}
                               :licenses {1 "approved" 2 "approved"}})
                   app
                   read-body
                   :id)]
    (testing "fetch reviewers"
      (let [reviewers (-> (request :get (str "/api/applications/reviewers"))
                          (authenticate api-key approver)
                          app
                          read-body)]
        (is (= ["alice" "bob" "carl" "developer" "owner"] (sort (map :userid reviewers))))
        (is (not (contains? (set (map :userid reviewers)) "invalid")))))
    (testing "reviews is not open without authentication"
      (let [response (-> (request :get (str "/api/applications/reviewers"))
                         app)]
        (is (= 401 (:status response)))))
    (testing "reviews is not open with applicant"
      (let [response (-> (request :get (str "/api/applications/reviewers"))
                         (authenticate api-key applicant)
                         app)]
        (is (= 403 (:status response)))))
    (testing "send review request"
      (-> (request :post (str "/api/applications/review_request"))
          (authenticate api-key approver)
          (json-body {:application-id app-id
                      :round 0
                      :comment "pls revu"
                      :recipients [reviewer]})
          app
          assert-response-is-ok))
    (testing "check review event"
      (let [events (-> (request :get (str "/api/applications/" app-id))
                       (authenticate api-key reviewer)
                       app
                       read-body
                       :application
                       :events)]
        (is (= [{:userid applicant :comment nil :event "apply"}
                {:userid reviewer :comment "pls revu" :event "review-request"}]
               (map #(select-keys % [:userid :comment :event]) events)))))
    (testing "send review"
      (-> (request :post (str "/api/applications/judge"))
          (authenticate api-key reviewer)
          (json-body {:command "third-party-review"
                      :application-id app-id
                      :round 0
                      :comment "is ok"})
          app
          assert-response-is-ok))
    (testing "approve"
      (-> (request :post (str "/api/applications/judge"))
          (authenticate api-key approver)
          (json-body {:command "approve"
                      :application-id app-id
                      :round 0
                      :comment "I approve this"})
          app
          assert-response-is-ok))
    (testing "events of approver"
      (let [events (-> (request :get (str "/api/applications/" app-id))
                       (authenticate api-key approver)
                       app
                       read-body
                       :application
                       :events)]
        (is (= [{:userid applicant :comment nil :event "apply"}
                {:userid reviewer :comment "pls revu" :event "review-request"}
                {:userid reviewer :comment "is ok" :event "third-party-review"}
                {:userid approver :comment "I approve this" :event "approve"}]
               (map #(select-keys % [:userid :comment :event]) events)))))
    (testing "events of reviewer"
      (let [events (-> (request :get (str "/api/applications/" app-id))
                       (authenticate api-key reviewer)
                       app
                       read-body
                       :application
                       :events)]
        (is (= [{:userid applicant :comment nil :event "apply"}
                {:userid reviewer :comment "pls revu" :event "review-request"}
                {:userid reviewer :comment "is ok" :event "third-party-review"}
                {:userid approver :comment "I approve this" :event "approve"}]
               (map #(select-keys % [:userid :comment :event]) events)))))
    (testing "events of applicant"
      (let [events (-> (request :get (str "/api/applications/" app-id))
                       (authenticate api-key applicant)
                       app
                       read-body
                       :application
                       :events)]
        (is (= [{:userid nil :comment nil :event "apply"}
                {:userid nil :comment "I approve this" :event "approve"}]
               (map #(select-keys % [:userid :comment :event]) events))
            "does not see review events nor users, but sees approval comment")))))
;; TODO non-happy path tests for review?

;; TODO test for event filtering when it gets implemented

(deftest application-api-add-member-test
  (let [api-key "42"
        user-id "alice"
        another-user "bob"
        catid 2
        app-id (-> (request :post (str "/api/applications/save"))
                   (authenticate api-key user-id)
                   (json-body {:command "save"
                               :catalogue-items [catid]
                               :items {1 "REST-Test"}})
                   app
                   assert-response-is-ok
                   read-body
                   :id)]
    (testing "happy path"
      (-> (request :post "/api/applications/add_member")
          (authenticate api-key user-id)
          (json-body {:application-id app-id
                      :member another-user})
          app
          assert-response-is-ok)
      (let [members (-> (request :get (str "/api/applications/" app-id))
                        (authenticate api-key user-id)
                        app
                        assert-response-is-ok
                        read-body
                        :application
                        :members)]
        (is (= ["bob"] members))))
    (testing "adding nonexistant user"
      (let [response (-> (request :post "/api/applications/add_member")
                         (authenticate api-key user-id)
                         (json-body {:application-id app-id
                                     :member "nonexistant"})
                         app)]
        ;; TODO: should be a bad request?
        (is (= 500 (:status response)))))
    (testing "adding as non-applicant"
      (let [response (-> (request :post "/api/applications/add_member")
                         (authenticate api-key "developer")
                         (json-body {:application-id app-id
                                     :member "developer"})
                         app)]
        (is (response-is-forbidden? response))))))

(defn- strip-cookie-attributes [cookie]
  (re-find #"[^;]*" cookie))

(defn- get-csrf-token [response]
  (let [token-regex #"var csrfToken = '([^\']*)'"
        [_ token] (re-find token-regex (:body response))]
    token))

(deftest application-api-session-test
  (let [username "alice"
        login-headers (-> (request :get "/Shibboleth.sso/Login" {:username username})
                          app
                          :headers)
        cookie (-> (get login-headers "Set-Cookie")
                   first
                   strip-cookie-attributes)
        csrf (-> (request :get "/")
                 (header "Cookie" cookie)
                 app
                 get-csrf-token)]
    (is cookie)
    (is csrf)
    (testing "submit with session"
      (let [body (-> (request :post (str "/api/applications/save"))
                     (header "Cookie" cookie)
                     (header "x-csrf-token" csrf)
                     (json-body {:command "submit"
                                 :catalogue-items [2]
                                 :items {1 "x" 2 "y" 3 "z"}
                                 :licenses {1 "approved" 2 "approved"}})
                     app
                     assert-response-is-ok
                     read-body)]
        (is (:success body))))
    (testing "submit with session but without csrf"
      (let [response (-> (request :post (str "/api/applications/save"))
                         (header "Cookie" cookie)
                         (json-body {:command "submit"
                                     :catalogue-items [2]
                                     :items {1 "x" 2 "y" 3 "z"}
                                     :licenses {1 "approved" 2 "approved"}})
                         app)]
        (is (response-is-unauthorized? response))))
    (testing "submit with session and csrf and wrong api-key"
      (let [response (-> (request :post (str "/api/applications/save"))
                         (header "Cookie" cookie)
                         (header "x-csrf-token" csrf)
                         (header "x-rems-api-key" "WRONG")
                         (json-body {:command "submit"
                                     :catalogue-items [2]
                                     :items {1 "x" 2 "y" 3 "z"}
                                     :licenses {1 "approved" 2 "approved"}})
                         app)
            body (read-body response)]
        (is (response-is-unauthorized? response))
        (is (= "invalid api key" body))))))

(def testfile (clojure.java.io/file "./test-data/test.txt"))

(def malicious-file (clojure.java.io/file "./test-data/malicious_test.html"))

(def filecontent {:tempfile testfile
                  :content-type "text/plain"
                  :filename "test.txt"
                  :size (.length testfile)})

(def malicious-content {:tempfile malicious-file
                        :content-type "text/html"
                        :filename "malicious_test.html"
                        :size (.length malicious-file)})

(deftest application-api-attachments-test
  (let [api-key "42"
        user-id "alice"
        catid 2
        field-id 5
        response (-> (request :post (str "/api/applications/save"))
                     (authenticate api-key user-id)
                     (json-body {:command "save"
                                 :catalogue-items [catid]
                                 :items {1 ""}})
                     app
                     read-body)
        app-id (:id response)]
    (testing "uploading attachment for a draft"
      (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
          (assoc :params {"file" filecontent})
          (assoc :multipart-params {"file" filecontent})
          (authenticate api-key user-id)
          app
          assert-response-is-ok))
    (testing "uploading malicious file for a draft"
      (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                         (assoc :params {"file" malicious-content})
                         (assoc :multipart-params {"file" malicious-content})
                         (authenticate api-key user-id)
                         app)]
        (is (= 400 (:status response)))))
    (testing "retrieving attachment for a draft"
      (let [response (-> (request :get (str "/api/applications/attachments/") {:application-id app-id :field-id field-id})
                         (authenticate api-key user-id)
                         app
                         assert-response-is-ok)]
        (is (= (slurp testfile) (slurp (:body response))))))
    (testing "uploading attachment as non-applicant"
      (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                         (assoc :params {"file" filecontent})
                         (assoc :multipart-params {"file" filecontent})
                         (authenticate api-key "carl")
                         app)]
        (is (response-is-forbidden? response))))
    (testing "retrieving attachment as non-applicant"
      (let [response (-> (request :get (str "/api/applications/attachments/") {:application-id app-id :field-id field-id})
                         (authenticate api-key "carl")
                         app)]
        (is (response-is-forbidden? response))))
    (testing "uploading attachment for a submitted application"
      (let [body (-> (request :post (str "/api/applications/save"))
                     (authenticate api-key user-id)
                     (json-body {:application-id app-id
                                 :command "submit"
                                 :catalogue-items [catid]
                                 :items {1 "x" 2 "y" 3 "z"}
                                 :licenses {1 "approved" 2 "approved"}})
                     app
                     read-body)]
        (is (= "applied" (:state body)))
        (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                           (assoc :params {"file" filecontent})
                           (assoc :multipart-params {"file" filecontent})
                           (authenticate api-key user-id)
                           app)]
          (is (response-is-forbidden? response)))))))

(deftest applications-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/applications"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body))))

  (testing "fetch application without authentication"
    (let [response (-> (request :get (str "/api/applications/1"))
                       app)
          body (read-body response)]
      (is (= body "unauthorized"))))
  (testing "fetch reviewers without authentication"
    (let [response (-> (request :get (str "/api/applications/reviewers"))
                       app)
          body (read-body response)]
      (is (= body "unauthorized"))))
  (testing "save without authentication"
    (let [response (-> (request :post (str "/api/applications/save"))
                       (json-body {:command "save"
                                   :catalogue-items [2]
                                   :items {1 "REST-Test"}})
                       app)
          body (read-body response)]
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "save with wrong API-Key"
    (let [response (-> (request :post (str "/api/applications/save"))
                       (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                       (json-body {:command "save"
                                   :catalogue-items [2]
                                   :items {1 "REST-Test"}})
                       app)
          body (read-body response)]
      (is (= "invalid api key" body))))
  (testing "judge without authentication"
    (let [body (-> (request :post (str "/api/applications/judge"))
                   (json-body {:command "approve"
                               :application-id 2
                               :round 0
                               :comment "msg"})
                   app
                   read-body)]
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "judge with wrong API-Key"
    (let [body (-> (request :post (str "/api/applications/judge"))
                   (authenticate "invalid-api-key" "developer")
                   (json-body {:command "approve"
                               :application-id 2
                               :round 0
                               :comment "msg"})
                   app
                   read-body)]
      (is (= "invalid api key" body))))
  (testing "upload attachment without authentication"
    (let [body (-> (request :post (str "/api/applications/add_attachment"))
                   (assoc :params {"file" filecontent})
                   (assoc :multipart-params {"file" filecontent})
                   app
                   read-body)]
      (is (str/includes? body "Invalid anti-forgery token"))))
  (testing "upload attachment with wrong API-Key"
    (let [body (-> (request :post (str "/api/applications/add_attachment"))
                   (assoc :params {"file" filecontent})
                   (assoc :multipart-params {"file" filecontent})
                   (authenticate "invalid-api-key" "developer")
                   app
                   read-body)]
      (is (= "invalid api key" body)))))

(deftest pdf-smoke-test
  (testing "not found"
    (let [response (-> (request :get (str "/api/applications/9999999/pdf"))
                       (authenticate "42" "developer")
                       app)]
      (is (= 404 (:status response)))))
  (testing "forbidden"
    (let [response (-> (request :get (str "/api/applications/2/pdf"))
                       (authenticate "42" "alice")
                       app)]
      (is (response-is-forbidden? response))))
  (testing "success"
    (let [response (-> (request :get (str "/api/applications/2/pdf"))
                       (authenticate "42" "developer")
                       app
                       assert-response-is-ok)]
      (is (= "application/pdf" (get-in response [:headers "Content-Type"])))
      (is (.startsWith (slurp (:body response)) "%PDF-1.")))))

(defn- create-form-with-fields [form-items]
  (-> (request :post "/api/forms/create")
      (authenticate "42" "owner")
      (json-body {:organization "abc"
                  :title ""
                  :items form-items})
      app
      read-ok-body
      :id))

(defn- create-catalogue-item-with-form [form-id]
  (-> (request :post "/api/catalogue-items/create")
      (authenticate "42" "owner")
      (json-body {:title ""
                  :form form-id
                  :resid 1
                  :wfid 1})
      app
      read-ok-body
      :id))

(defn- create-application-draft-for-catalogue-item [cat-item-id]
  (-> (request :get (str "/api/applications/draft?catalogue-items=" cat-item-id))
      (authenticate "42" "alice")
      app
      read-ok-body))

(defn- save-application [command]
  (-> (request :post (str "/api/applications/save"))
      (authenticate "42" "alice")
      (json-body command)
      app
      read-ok-body
      :id))

(defn- get-application-description-through-api-1 [app-id]
  (get-in (-> (request :get (str "/api/applications/" app-id))
              (authenticate "42" "alice")
              app
              read-ok-body)
          [:application :description]))

(defn- get-application-description-through-api-2 [app-id]
  (get-in (-> (request :get (str "/api/applications/"))
              (authenticate "42" "alice")
              app
              read-ok-body
              (->> (filter #(= app-id (:id %))))
              first)
          [:description]))

(deftest application-description-test
  (testing "applications without description field have no description"
    (let [form-id (create-form-with-fields [])
          cat-item-id (create-catalogue-item-with-form form-id)
          app-id (save-application {:command "save"
                                    :catalogue-items [cat-item-id]
                                    :items {}
                                    :licenses {}})]
      (is (= nil
             (get-application-description-through-api-1 app-id)
             (get-application-description-through-api-2 app-id)))))

  (testing "applications with description field have a description"
    (let [form-id (create-form-with-fields [{:title {:en ""}
                                             :optional false
                                             :type "description"
                                             :input-prompt {:en ""}}])
          cat-item-id (create-catalogue-item-with-form form-id)
          draft (create-application-draft-for-catalogue-item cat-item-id)
          app-id (save-application {:command "save"
                                    :catalogue-items [cat-item-id]
                                    :items {(get-in draft [:items 0 :id]) "some description text"}
                                    :licenses {}})]
      (is (= "some description text"
             (get-application-description-through-api-1 app-id)
             (get-application-description-through-api-2 app-id))))))

(defn- send-dynamic-command [actor cmd]
  (-> (request :post (str "/api/applications/command"))
      (authenticate "42" actor)
      (json-body cmd)
      app
      read-body))

(defn- get-application [actor id]
  (-> (request :get (str "/api/applications/" id))
      (authenticate "42" actor)
      app
      read-body))

(deftest dynamic-applications-test
  (let [api-key "42"
        user-id "alice"
        handler-id "developer"
        decider-id "bob"
        application-id 12] ;; submitted dynamic application from test data

    (testing "getting dynamic application as applicant"
      (let [data (get-application user-id application-id)]
        (is (= "workflow/dynamic" (get-in data [:application :workflow :type])))
        (is (= [{:actor user-id
                 :application-id application-id
                 :event "event/submitted"
                 :time nil}]
               (get-in data [:application :dynamic-events])))
        (is (= ["rems.workflow.dynamic/add-member"] (get-in data [:application :possible-commands])))))

    (testing "getting dynamic application as handler"
      (let [data (get-application handler-id application-id)]
        (is (= "workflow/dynamic" (get-in data [:application :workflow :type])))
        (is (= #{"rems.workflow.dynamic/request-comment"
                 "rems.workflow.dynamic/request-decision"
                 "rems.workflow.dynamic/reject"
                 "rems.workflow.dynamic/approve"
                 "rems.workflow.dynamic/return"}
               (set (get-in data [:application :possible-commands]))))))

    (testing "send command without user"
      (is (= {:success false
              :errors ["forbidden"]}
             (send-dynamic-command "" {:type :rems.workflow.dynamic/approve
                                       :application-id application-id}))
          "user should be forbidden to send command"))

    (testing "send command with a user that is not a handler"
      (is (= {:success false
              :errors ["forbidden"]}
             (send-dynamic-command user-id {:type :rems.workflow.dynamic/approve
                                            :application-id application-id}))
          "user should be forbidden to send command"))

    (testing "send commands with authorized user"
      (testing "request-decision"
        (is (= {:success true} (send-dynamic-command handler-id
                                                     {:type :rems.workflow.dynamic/request-decision
                                                      :application-id application-id
                                                      :decider decider-id})))
        (let [data (get-application handler-id application-id)]
          (is (= {:id application-id
                  :decider decider-id
                  :state "rems.workflow.dynamic/submitted"}
                 (select-keys (:application data) [:id :decider :state])))))
      (testing "decide"
        (is (= {:success true} (send-dynamic-command decider-id
                                                     {:type :rems.workflow.dynamic/decide
                                                      :application-id application-id
                                                      :decision :approved})))
        (let [data (get-application handler-id application-id)]
          (is (= {:id application-id
                  :decision "approved"
                  :state "rems.workflow.dynamic/submitted"}
                 (select-keys (:application data) [:id :decider :decision :state])))))
      (testing "approve"
        (is (= {:success true} (send-dynamic-command handler-id {:type :rems.workflow.dynamic/approve
                                                                 :application-id application-id})))
        (let [data (get-application handler-id application-id)]
          (is (= {:id application-id
                  :state "rems.workflow.dynamic/approved"}
                 (select-keys (:application data) [:id :state])))
          (is (= ["event/submitted"
                  "event/decision-requested"
                  "event/decided"
                  "event/approved"]
                 (map :event (get-in data [:application :dynamic-events])))))))))

(deftest dynamic-application-create-test
  (let [api-key "42"
        user-id "alice"
        catid 9 ;; catalogue item with dynamic workflow in test-data
        draft (create-application-draft-for-catalogue-item 9)]
    (testing "get draft"
      (is (< 6 (count (:items draft)))))
    (let [response (-> (request :post (str "/api/applications/save"))
                       (authenticate api-key user-id)
                       (json-body {:command "save"
                                   :catalogue-items [catid]
                                   :items {1 "dynamic test"}})
                       app
                       read-body)
          application-id (:id response)]
      (testing "create application"
        (is (some? application-id))
        (let [saved (get-application user-id application-id)]
          (is (= "workflow/dynamic" (get-in saved [:application :workflow :type])))
          (is (= "rems.workflow.dynamic/draft" (get-in saved [:application :state])))
          (is (= "dynamic test" (get-in saved [:items 0 :value])))))
      (testing "can't submit with missing required fields"
        (is (= {:success false :errors [{:type "t.form.validation/required" :field-id 2}]}
               (send-dynamic-command user-id {:type :rems.workflow.dynamic/submit
                                              :application-id application-id}))))
      (testing "add missing fields"
        (let [save-again (-> (request :post (str "/api/applications/save"))
                             (authenticate api-key user-id)
                             (json-body {:command "save"
                                         :application-id application-id
                                         :items {1 "dynamic test2"
                                                 2 "purpose"}})
                             app
                             read-body)
              saved (get-application user-id application-id)]
          (is (true? (:success save-again)))
          (is (= application-id (:id save-again)))
          (is (= "dynamic test2" (get-in saved [:items 0 :value])))))
      (testing "old-style submit fails"
        (let [try-submit (-> (request :post (str "/api/applications/save"))
                             (authenticate api-key user-id)
                             (json-body {:command "submit"
                                         :application-id application-id
                                         :items {}})
                             app)]
          (is (= 400 (:status try-submit)))
          (is (= "Can not submit dynamic application via /save" (read-body try-submit)))))
      (testing "submitting"
        (is (= {:success true} (send-dynamic-command user-id {:type :rems.workflow.dynamic/submit
                                                              :application-id application-id})))
        (let [submitted (get-application user-id application-id)]
          (is (= "rems.workflow.dynamic/submitted" (get-in submitted [:application :state])))
          (is (= ["event/submitted"]
                 (map :event (get-in submitted [:application :dynamic-events])))))))))
