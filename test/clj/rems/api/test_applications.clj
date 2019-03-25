(ns ^:integration rems.api.test-applications
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

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
    (testing "save with session"
      (let [body (-> (request :post (str "/api/applications/save"))
                     (header "Cookie" cookie)
                     (header "x-csrf-token" csrf)
                     (json-body {:command "save"
                                 :catalogue-items [2]
                                 :items {1 "x" 2 "y" 3 "z"}
                                 :licenses {1 "approved" 2 "approved"}})
                     app
                     assert-response-is-ok
                     read-body)]
        (is (:success body))))
    (testing "save with session but without csrf"
      (let [response (-> (request :post (str "/api/applications/save"))
                         (header "Cookie" cookie)
                         (json-body {:command "save"
                                     :catalogue-items [2]
                                     :items {1 "x" 2 "y" 3 "z"}
                                     :licenses {1 "approved" 2 "approved"}})
                         app)]
        (is (response-is-unauthorized? response))))
    (testing "save with session and csrf and wrong api-key"
      (let [response (-> (request :post (str "/api/applications/save"))
                         (header "Cookie" cookie)
                         (header "x-csrf-token" csrf)
                         (header "x-rems-api-key" "WRONG")
                         (json-body {:command "save"
                                     :catalogue-items [2]
                                     :items {1 "x" 2 "y" 3 "z"}
                                     :licenses {1 "approved" 2 "approved"}})
                         app)
            body (read-body response)]
        (is (response-is-unauthorized? response))
        (is (= "invalid api key" body))))))

(deftest pdf-smoke-test
  (testing "not found"
    (let [response (-> (request :get (str "/api/applications/9999999/pdf"))
                       (authenticate "42" "developer")
                       app)]
      (is (response-is-not-found? response))))
  (testing "forbidden"
    (let [response (-> (request :get (str "/api/applications/13/pdf"))
                       (authenticate "42" "bob")
                       app)]
      (is (response-is-forbidden? response))))
  (testing "success"
    (let [response (-> (request :get (str "/api/applications/13/pdf"))
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

(defn- create-empty-form []
  (create-form-with-fields []))

(defn- create-catalogue-item [form-id workflow-id]
  (-> (request :post "/api/catalogue-items/create")
      (authenticate "42" "owner")
      (json-body {:title ""
                  :form form-id
                  :resid 1
                  :wfid workflow-id
                  :state "enabled"})
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

(defn- send-dynamic-command [actor cmd]
  (-> (request :post (str "/api/applications/command"))
      (authenticate "42" actor)
      (json-body cmd)
      app
      read-body))

;; TODO refactor tests to use true v2 api
(defn- get-application [actor id]
  (-> (request :get (str "/api/v2/applications/" id "/migration"))
      (authenticate "42" actor)
      app
      read-body))

(deftest dynamic-applications-test
  (let [api-key "42"
        user-id "alice"
        handler-id "developer"
        commenter-id "carl"
        decider-id "bob"
        application-id 11] ;; submitted dynamic application from test data

    (testing "getting dynamic application as applicant"
      (let [data (get-application user-id application-id)]
        (is (= "workflow/dynamic" (get-in data [:application :workflow :type])))
        (is (= ["application.event/created"
                "application.event/draft-saved"
                "application.event/submitted"]
               (map :event/type (get-in data [:application :dynamic-events]))))
        (is (= ["rems.workflow.dynamic/remove-member"
                "rems.workflow.dynamic/uninvite-member"]
               (get-in data [:application :possible-commands])))))

    (testing "getting dynamic application as handler"
      (let [data (get-application handler-id application-id)]
        (is (= "workflow/dynamic" (get-in data [:application :workflow :type])))
        (is (= #{"rems.workflow.dynamic/request-comment"
                 "rems.workflow.dynamic/request-decision"
                 "rems.workflow.dynamic/reject"
                 "rems.workflow.dynamic/approve"
                 "rems.workflow.dynamic/return"
                 "rems.workflow.dynamic/add-member"
                 "rems.workflow.dynamic/remove-member"
                 "rems.workflow.dynamic/invite-member"
                 "rems.workflow.dynamic/uninvite-member"
                 "see-everything"}
               (set (get-in data [:application :possible-commands]))))))

    (testing "send command without user"
      (is (= {:success false
              :errors [{:type "forbidden"}]}
             (send-dynamic-command "" {:type :rems.workflow.dynamic/approve
                                       :application-id application-id}))
          "user should be forbidden to send command"))

    (testing "send command with a user that is not a handler"
      (is (= {:success false
              :errors [{:type "forbidden"}]}
             (send-dynamic-command user-id {:type :rems.workflow.dynamic/approve
                                            :application-id application-id
                                            :comment ""}))
          "user should be forbidden to send command"))

    (testing "send commands with authorized user"
      (testing "even handler cannot comment without request"
        (is (= {:errors [{:type "forbidden"}], :success false}
               (send-dynamic-command handler-id
                                     {:type :rems.workflow.dynamic/comment
                                      :application-id application-id
                                      :comment "What am I commenting on?"}))))
      (testing "comment with request"
        (let [eventcount (count (get-in (get-application handler-id application-id)
                                        [:application :dynamic-events]))]
          (testing "requesting comment"
            (is (= {:success true} (send-dynamic-command handler-id
                                                         {:type :rems.workflow.dynamic/request-comment
                                                          :application-id application-id
                                                          :commenters [decider-id commenter-id]
                                                          :comment "What say you?"}))))
          (testing "commenter can now comment"
            (is (= {:success true} (send-dynamic-command commenter-id
                                                         {:type :rems.workflow.dynamic/comment
                                                          :application-id application-id
                                                          :comment "Yeah, I dunno"}))))
          (testing "comment was linked to request"
            (let [application (get-application handler-id application-id)
                  request-event (get-in application [:application :dynamic-events eventcount])
                  comment-event (get-in application [:application :dynamic-events (inc eventcount)])]
              (is (= (:application/request-id request-event)
                     (:application/request-id comment-event)))))))
      (testing "request-decision"
        (is (= {:success true} (send-dynamic-command handler-id
                                                     {:type :rems.workflow.dynamic/request-decision
                                                      :application-id application-id
                                                      :deciders [decider-id]
                                                      :comment ""}))))
      (testing "decide"
        (is (= {:success true} (send-dynamic-command decider-id
                                                     {:type :rems.workflow.dynamic/decide
                                                      :application-id application-id
                                                      :decision :approved
                                                      :comment ""}))))
      (testing "approve"
        (is (= {:success true} (send-dynamic-command handler-id {:type :rems.workflow.dynamic/approve
                                                                 :application-id application-id
                                                                 :comment ""})))
        (let [handler-data (get-application handler-id application-id)
              handler-event-types (map :event/type (get-in handler-data [:application :dynamic-events]))
              applicant-data (get-application user-id application-id)
              applicant-event-types (map :event/type (get-in applicant-data [:application :dynamic-events]))]
          (testing "handler can see all events"
            (is (= {:id application-id
                    :state "rems.workflow.dynamic/approved"}
                   (select-keys (:application handler-data) [:id :state])))
            (is (= ["application.event/created"
                    "application.event/draft-saved"
                    "application.event/submitted"
                    "application.event/comment-requested"
                    "application.event/commented"
                    "application.event/decision-requested"
                    "application.event/decided"
                    "application.event/approved"]
                   handler-event-types)))
          (testing "applicant cannot see all events"
            (is (= ["application.event/created"
                    "application.event/draft-saved"
                    "application.event/submitted"
                    "application.event/approved"]
                   applicant-event-types))))))))

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
      (testing "getting application as other user is forbidden"
        (is (response-is-forbidden?
             (-> (request :get (str "/api/v2/applications/" application-id))
                 (authenticate api-key "bob")
                 app))))
      (testing "saving fields as other user is forbidden"
        (is (response-is-forbidden?
             (-> (request :post "/api/applications/save")
                 (authenticate api-key "bob")
                 (json-body {:command "save"
                             :application-id application-id
                             :items {1 "xxxxx"}})
                 app))))
      (testing "can't submit with missing required fields"
        (is (= {:success false :errors [{:type "t.form.validation/required" :field-id 2}
                                        {:type "t.form.validation/required" :license-id 1}
                                        {:type "t.form.validation/required" :license-id 2}]}
               (send-dynamic-command user-id {:type :rems.workflow.dynamic/submit
                                              :application-id application-id}))))
      (testing "add missing fields"
        (let [save-again (-> (request :post (str "/api/applications/save"))
                             (authenticate api-key user-id)
                             (json-body {:command "save"
                                         :application-id application-id
                                         :items {1 "dynamic test2"
                                                 2 "purpose"}
                                         :licenses {1 "approved" 2 "approved"}})
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
                                         :items {}
                                         :licenses {1 "approved" 2 "approved"}})
                             app)]
          (is (= 400 (:status try-submit)))
          (is (= "Can not submit dynamic application via /save" (read-body try-submit)))))
      (testing "submitting"
        (is (= {:success true} (send-dynamic-command user-id {:type :rems.workflow.dynamic/submit
                                                              :application-id application-id})))
        (let [submitted (get-application user-id application-id)]
          (is (= "rems.workflow.dynamic/submitted" (get-in submitted [:application :state])))
          (is (= ["application.event/created"
                  "application.event/draft-saved"
                  "application.event/draft-saved"
                  "application.event/submitted"]
                 (map :event/type (get-in submitted [:application :dynamic-events])))))))))

(deftest disabled-catalogue-item
  (let [api-key "42"
        applicant "alice"
        handler "developer"
        disabled-catalogue-item 11
        draft-with-disabled 17
        submitted-with-disabled 18]
    (testing "save is forbidden for an application with a disabled item"
      (let [response (-> (request :post (str "/api/applications/save"))
                         (authenticate api-key applicant)
                         (json-body {:command "save"
                                     :catalogue-items [disabled-catalogue-item]
                                     :items {1 ""}})
                         app)]
        (is (= 400 (:status response)))))
    (testing "submit is forbidden for an application with a disabled item"
      (is (= {:success false :errors [{:type "forbidden"}]}
             (send-dynamic-command applicant {:type :rems.workflow.dynamic/submit
                                              :application-id draft-with-disabled}))))
    (testing "approve is allowed for a submitted application with a disabled item"
      (is (= {:success true}
             (send-dynamic-command handler {:type :rems.workflow.dynamic/approve
                                            :application-id submitted-with-disabled
                                            :comment "Looks fine."}))))))

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
        catid 9
        field-id 5
        app-id (save-application {:command "save"
                                  :catalogue-items [catid]
                                  :items {1 "x" 2 "x"}
                                  :licenses {1 "approved" 2 "approved"}})]
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
    (testing "submit application"
      (is (= {:success true} (send-dynamic-command user-id {:type :rems.workflow.dynamic/submit
                                                            :application-id app-id}))))
    (testing "uploading attachment for a submitted application"
      (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                         (assoc :params {"file" filecontent})
                         (assoc :multipart-params {"file" filecontent})
                         (authenticate api-key user-id)
                         app)]
        (is (response-is-forbidden? response))))))

(deftest applications-api-security-test
  (testing "fetch application without authentication"
    (is (response-is-unauthorized? (-> (request :get (str "/api/v2/applications/13"))
                                       app))))
  (testing "fetch deciders without authentication"
    (is (response-is-unauthorized? (-> (request :get (str "/api/applications/deciders"))
                                       app))))
  (testing "save without authentication"
    (is (response-is-unauthorized? (-> (request :post (str "/api/applications/save"))
                                       (json-body {:command "save"
                                                   :catalogue-items [2]
                                                   :items {1 "REST-Test"}})
                                       app))))
  (testing "save with wrong API-Key"
    (is (response-is-unauthorized? (-> (request :post (str "/api/applications/save"))
                                       (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                                       (json-body {:command "save"
                                                   :catalogue-items [2]
                                                   :items {1 "REST-Test"}})
                                       app))))
  (let [app-id (save-application {:command "save"
                                  :catalogue-items [9]
                                  :items {1 "x" 2 "x"}
                                  :licenses {1 "approved" 2 "approved"}})]
    (is (pos? app-id))
    (testing "send command without authentication"
      (is (response-is-unauthorized? (-> (request :post (str "/api/applications/command"))
                                         (json-body {:type :rems.workflow.dynamic/submit
                                                     :application-id app-id})
                                         app))))
    (testing "send command with wrong api-key"
      (is (response-is-unauthorized? (-> (request :post (str "/api/applications/command"))
                                         (authenticate "invalid-api-key" "alice")
                                         (json-body {:type :rems.workflow.dynamic/submit
                                                     :application-id app-id})
                                         app)))))
  (testing "upload attachment without authentication"
    (is (response-is-unauthorized? (-> (request :post (str "/api/applications/add_attachment"))
                                       (assoc :params {"file" filecontent})
                                       (assoc :multipart-params {"file" filecontent})
                                       app))))
  (testing "upload attachment with wrong API-Key"
    (is (response-is-unauthorized? (-> (request :post (str "/api/applications/add_attachment"))
                                       (assoc :params {"file" filecontent})
                                       (assoc :multipart-params {"file" filecontent})
                                       (authenticate "invalid-api-key" "developer")
                                       app)))))

(defn- create-dynamic-workflow []
  (-> (request :post "/api/workflows/create")
      (json-body {:organization "abc"
                  :title "dynamic workflow"
                  :type :dynamic
                  :handlers ["developer"]})
      (authenticate "42" "owner")
      app
      read-ok-body
      :id))

(defn- create-v2-application [catalogue-item-ids user-id]
  (-> (request :post (str "/api/v2/applications/create"))
      (authenticate "42" user-id)
      (json-body {:catalogue-item-ids catalogue-item-ids})
      app
      read-ok-body
      :application-id))

(defn- create-dynamic-application [user-id]
  (let [form-id (create-empty-form)
        workflow-id (create-dynamic-workflow)
        cat-item-id (create-catalogue-item form-id workflow-id)]
    (create-v2-application [cat-item-id] user-id)))

(defn- get-ids [applications]
  (set (map :application/id applications)))

(defn- get-v2-applications [user-id]
  (-> (request :get "/api/v2/applications")
      (authenticate "42" user-id)
      app
      read-ok-body))

(defn- get-v2-application [app-id user-id]
  (-> (request :get (str "/api/v2/applications/" app-id))
      (authenticate "42" user-id)
      app
      read-ok-body))

(deftest test-v2-application-api
  (let [app-id (create-dynamic-application "alice")]

    (testing "list user applications"
      (is (contains? (get-ids (get-v2-applications "alice"))
                     app-id)))

    (testing "get single application"
      (is (= app-id
             (:application/id (get-v2-application app-id "alice")))))))

(defn- get-v2-open-reviews [user-id]
  (-> (request :get "/api/v2/reviews/open")
      (authenticate "42" user-id)
      app
      read-ok-body))

(defn- get-v2-handled-reviews [user-id]
  (-> (request :get "/api/v2/reviews/handled")
      (authenticate "42" user-id)
      app
      read-ok-body))

(deftest test-v2-review-api
  (let [app-id (create-dynamic-application "alice")]

    (testing "does not list drafts"
      (is (not (contains? (get-ids (get-v2-open-reviews "developer"))
                          app-id))))

    (testing "lists submitted in open reviews"
      (is (= {:success true} (send-dynamic-command "alice" {:type :rems.workflow.dynamic/submit
                                                            :application-id app-id})))
      (is (contains? (get-ids (get-v2-open-reviews "developer"))
                     app-id))
      (is (not (contains? (get-ids (get-v2-handled-reviews "developer"))
                          app-id))))

    (testing "lists handled in handled reviews"
      (is (= {:success true} (send-dynamic-command "developer" {:type :rems.workflow.dynamic/approve
                                                                :application-id app-id
                                                                :comment ""})))
      (is (not (contains? (get-ids (get-v2-open-reviews "developer"))
                          app-id)))
      (is (contains? (get-ids (get-v2-handled-reviews "developer"))
                     app-id)))))
