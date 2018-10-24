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
                           app)
              data (read-body response)]
          (is (response-is-ok? response))
          (is (= "application/json; charset=utf-8" (get-in response [:headers "Content-Type"])))
          (is (= [1 2 3 4 5 6 7] (map :id (sort-by :id data))))))
      (testing "transit support"
        (let [response (-> (request :get "/api/applications")
                           (authenticate api-key user-id)
                           (header "Accept" "application/transit+json")
                           app)
              data (read-body response)]
          (is (response-is-ok? response))
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
        (let [response (-> (request :get (str "/api/applications/" application-id))
                           (authenticate api-key user-id)
                           app)
              application (read-body response)]
          (is (not (:errors application)))
          (is (= application-id (:id (:application application))))
          (is (= "draft" (:state (:application application))))
          (is (= 2 (count (:licenses application))))
          (is (= 4 (count (:items application))))))
      (testing "retrieving as other user"
        (let [response (-> (request :get (str "/api/applications/" application-id))
                           (authenticate api-key another-user)
                           app)]
          (is (response-is-unauthorized? response))))
      (testing "saving as other user"
        (let [response (-> (request :post (str "/api/applications/save"))
                           (authenticate api-key another-user)
                           (json-body {:command "save"
                                       :application-id application-id
                                       :items {1 "REST-Test"}})
                           app)]
          (is (response-is-unauthorized? response))))
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
        (is (= 4 (count validations)))
        (is (some #(.contains (:text %) "Project name") validations))
        (is (some #(.contains (:text %) "Purpose of the project") validations))
        (is (some #(.contains (:text %) "non-localized link license") validations))
        (is (some #(.contains (:text %) "non-localized text license") validations)))
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
                       app)
          cmd-response (read-body response)
          app-id (:id cmd-response)]
      (is (response-is-ok? response))
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
        (is (response-is-ok? (-> (request :post (str "/api/applications/judge"))
                                 (authenticate api-key approver)
                                 (json-body {:command "approve"
                                             :application-id app-id
                                             :round 0
                                             :comment "msg"})
                                 app))))
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
                 (is (response-is-ok? (-> (request :post (str "/api/applications/save"))
                                          (authenticate api-key user)
                                          (json-body {:command "submit"
                                                      :application-id app-id
                                                      :items {1 "x" 2 "y" 3 "z"}
                                                      :licenses {1 "approved" 2 "approved"}})
                                          app))))
        action (fn [body]
                 (is (response-is-ok? (-> (request :post (str "/api/applications/judge"))
                                          (authenticate api-key user)
                                          (json-body (merge {:application-id app-id
                                                             :round 0}
                                                            body))
                                          app))))]
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
                       app)
          events (-> response
                     read-body
                     :application
                     :events)]
      (is (response-is-ok? response))
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
    (testing "send review request"
      (is (response-is-ok? (-> (request :post (str "/api/applications/review_request"))
                               (authenticate api-key approver)
                               (json-body {:application-id app-id
                                           :round 0
                                           :comment "pls revu"
                                           :recipients [reviewer]})
                               app))))
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
      (is (response-is-ok? (-> (request :post (str "/api/applications/judge"))
                               (authenticate api-key reviewer)
                               (json-body {:command "third-party-review"
                                           :application-id app-id
                                           :round 0
                                           :comment "is ok"})
                               app))))
    (testing "approve"
      (is (response-is-ok? (-> (request :post (str "/api/applications/judge"))
                               (authenticate api-key approver)
                               (json-body {:command "approve"
                                           :application-id app-id
                                           :round 0
                                           :comment "I approve this"})
                               app))))
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
                   read-body
                   :id)]
    (testing "happy path"
      (let [response (-> (request :post "/api/applications/add_member")
                         (authenticate api-key user-id)
                         (json-body {:application-id app-id
                                     :member another-user})
                         app
                         read-body)]
        (is (:success response))
        (let [members (-> (request :get (str "/api/applications/" app-id))
                          (authenticate api-key user-id)
                          app
                          read-body
                          :application
                          :members)]
          (is (= ["bob"] members)))))
    (testing "adding nonexistant user"
      (let [response (-> (request :post "/api/applications/add_member")
                         (authenticate api-key user-id)
                         (json-body {:application-id app-id
                                     :member "nonexistant"})
                         app)]
        ;; this is a 500 currently...
        (is (not (response-is-ok? response)))))
    (testing "adding as non-applicant"
      (let [response (-> (request :post "/api/applications/add_member")
                         (authenticate api-key "developer")
                         (json-body {:application-id app-id
                                     :member "developer"})
                         app)]
        (is (response-is-unauthorized? response))))))

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
      (let [response (-> (request :post (str "/api/applications/save"))
                         (header "Cookie" cookie)
                         (header "x-csrf-token" csrf)
                         (json-body {:command "submit"
                                     :catalogue-items [2]
                                     :items {1 "x" 2 "y" 3 "z"}
                                     :licenses {1 "approved" 2 "approved"}})
                         app)
            body (read-body response)]
        (is (response-is-ok? response))
        (is (:success body))))
    (testing "submit with session but without csrf"
      (let [response (-> (request :post (str "/api/applications/save"))
                         (header "Cookie" cookie)
                         (json-body {:command "submit"
                                     :catalogue-items [2]
                                     :items {1 "x" 2 "y" 3 "z"}
                                     :licenses {1 "approved" 2 "approved"}})
                         app)]
        (is (response-is-forbidden? response))))
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

(def filecontent {:tempfile testfile
                  :content-type "text/plain"
                  :filename "test.txt"
                  :size (.length testfile)})

(deftest application-api-attachments
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
      (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                         (assoc :params {"file" filecontent})
                         (assoc :multipart-params {"file" filecontent})
                         (authenticate api-key user-id)
                         app)]
        (is (response-is-ok? response))))
    (testing "retrieving attachment for a draft"
      (let [response (-> (request :get (str "/api/applications/attachments/") {:application-id app-id :field-id field-id})
                         (authenticate api-key user-id)
                         app)]
        (is (response-is-ok? response))
        (is (= (slurp testfile) (slurp (:body response))))))
    (testing "uploading attachment as non-applicant"
      (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                         (assoc :params {"file" filecontent})
                         (assoc :multipart-params {"file" filecontent})
                         (authenticate api-key "carl")
                         app)]
        (is (response-is-unauthorized? response))))
    (testing "retrieving attachment as non-applicant"
      (let [response (-> (request :get (str "/api/applications/attachments/") {:application-id app-id :field-id field-id})
                         (authenticate api-key "carl")
                         app)]
        (is (response-is-unauthorized? response))))
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
          (is (response-is-unauthorized? response)))))))

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
  (testing "not authorized"
    (let [response (-> (request :get (str "/api/applications/2/pdf"))
                       (authenticate "42" "alice")
                       app)]
      (is (response-is-unauthorized? response))))
  (testing "success"
    (let [response (-> (request :get (str "/api/applications/2/pdf"))
                       (authenticate "42" "developer")
                       app)]
      (is (response-is-ok? response))
      (is (= "application/pdf" (get-in response [:headers "Content-Type"])))
      (is (.startsWith (slurp (:body response)) "%PDF-1.")))))
