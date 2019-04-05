(ns ^:integration rems.api.test-applications
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.form :as form]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

;;; shared helpers

(defn- create-dynamic-workflow []
  (-> (request :post "/api/workflows/create")
      (json-body {:organization "abc"
                  :title "dynamic workflow"
                  :type :dynamic
                  :handlers ["developer"]})
      (authenticate "42" "owner")
      handler
      read-ok-body
      :id))

(defn- create-form-with-fields [form-items]
  (-> (request :post "/api/forms/create")
      (authenticate "42" "owner")
      (json-body {:organization "abc"
                  :title ""
                  :items form-items})
      handler
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
                  :wfid workflow-id})
      handler
      read-ok-body
      :id))

(defn- create-dymmy-catalogue-item []
  (let [form-id (create-empty-form)
        workflow-id (create-dynamic-workflow)]
    (create-catalogue-item form-id workflow-id)))

(defn- send-dynamic-command [actor cmd]
  (-> (request :post (str "/api/applications/command/" (name (:type cmd))))
      (authenticate "42" actor)
      (json-body (dissoc cmd :type))
      handler
      read-body))

(defn- create-v2-application [catalogue-item-ids user-id]
  (-> (request :post "/api/v2/applications/create")
      (authenticate "42" user-id)
      (json-body {:catalogue-item-ids catalogue-item-ids})
      handler
      read-ok-body
      :application-id))

(defn- create-dynamic-application [user-id]
  (create-v2-application [(create-dymmy-catalogue-item)] user-id))

(defn- get-ids [applications]
  (set (map :application/id applications)))

(defn- get-v2-applications [user-id]
  (-> (request :get "/api/v2/applications")
      (authenticate "42" user-id)
      handler
      read-ok-body))

(defn- get-v2-application [app-id user-id]
  (-> (request :get (str "/api/v2/applications/" app-id))
      (authenticate "42" user-id)
      handler
      read-ok-body))

(defn- get-v2-open-reviews [user-id]
  (-> (request :get "/api/v2/reviews/open")
      (authenticate "42" user-id)
      handler
      read-ok-body))

(defn- get-v2-handled-reviews [user-id]
  (-> (request :get "/api/v2/reviews/handled")
      (authenticate "42" user-id)
      handler
      read-ok-body))

;;; tests

(defn- strip-cookie-attributes [cookie]
  (re-find #"[^;]*" cookie))

(defn- get-csrf-token [response]
  (let [token-regex #"var csrfToken = '([^\']*)'"
        [_ token] (re-find token-regex (:body response))]
    token))

(deftest application-api-session-test
  (let [username "alice"
        login-headers (-> (request :get "/Shibboleth.sso/Login" {:username username})
                          handler
                          :headers)
        cookie (-> (get login-headers "Set-Cookie")
                   first
                   strip-cookie-attributes)
        csrf (-> (request :get "/")
                 (header "Cookie" cookie)
                 handler
                 get-csrf-token)
        cat-id (create-dymmy-catalogue-item)]
    (is cookie)
    (is csrf)
    (testing "save with session"
      (let [body (-> (request :post "/api/v2/applications/create")
                     (header "Cookie" cookie)
                     (header "x-csrf-token" csrf)
                     (json-body {:catalogue-item-ids [cat-id]})
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:success body))))
    (testing "save with session but without csrf"
      (let [response (-> (request :post "/api/v2/applications/create")
                         (header "Cookie" cookie)
                         (json-body {:catalogue-item-ids [cat-id]})
                         handler)]
        (is (response-is-unauthorized? response))))
    (testing "save with session and csrf and wrong api-key"
      (let [response (-> (request :post "/api/v2/applications/create")
                         (header "Cookie" cookie)
                         (header "x-csrf-token" csrf)
                         (header "x-rems-api-key" "WRONG")
                         (json-body {:catalogue-item-ids [cat-id]})
                         handler)
            body (read-body response)]
        (is (response-is-unauthorized? response))
        (is (= "invalid api key" body))))))

(deftest pdf-smoke-test
  (testing "not found"
    (let [response (-> (request :get "/api/applications/9999999/pdf")
                       (authenticate "42" "developer")
                       handler)]
      (is (response-is-not-found? response))))
  (testing "forbidden"
    (let [response (-> (request :get "/api/applications/13/pdf")
                       (authenticate "42" "bob")
                       handler)]
      (is (response-is-forbidden? response))))
  (testing "success"
    (let [response (-> (request :get "/api/applications/13/pdf")
                       (authenticate "42" "developer")
                       handler
                       assert-response-is-ok)]
      (is (= "application/pdf" (get-in response [:headers "Content-Type"])))
      (is (.startsWith (slurp (:body response)) "%PDF-1.")))))

(deftest dynamic-applications-test
  (let [user-id "alice"
        handler-id "developer"
        commenter-id "carl"
        decider-id "bob"
        application-id 11] ;; submitted dynamic application from test data

    (testing "getting dynamic application as applicant"
      (let [application (get-v2-application application-id user-id)]
        (is (= "workflow/dynamic" (get-in application [:application/workflow :workflow/type])))
        (is (= ["application.event/created"
                "application.event/draft-saved"
                "application.event/submitted"]
               (map :event/type (get application :application/events))))
        (is (= #{"application.command/remove-member"
                 "application.command/uninvite-member"}
               (set (get application :application/permissions))))))

    (testing "getting dynamic application as handler"
      (let [application (get-v2-application application-id handler-id)]
        (is (= "workflow/dynamic" (get-in application [:application/workflow :workflow/type])))
        (is (= #{"application.command/request-comment"
                 "application.command/request-decision"
                 "application.command/reject"
                 "application.command/approve"
                 "application.command/return"
                 "application.command/add-member"
                 "application.command/remove-member"
                 "application.command/invite-member"
                 "application.command/uninvite-member"
                 "see-everything"}
               (set (get application :application/permissions))))))

    (testing "send command without user"
      (is (= {:success false
              :errors [{:type "forbidden"}]}
             (send-dynamic-command "" {:type :application.command/approve
                                       :comment "" :application-id application-id}))
          "user should be forbidden to send command"))

    (testing "send command with a user that is not a handler"
      (is (= {:success false
              :errors [{:type "forbidden"}]}
             (send-dynamic-command user-id {:type :application.command/approve
                                            :application-id application-id
                                            :comment ""}))
          "user should be forbidden to send command"))

    (testing "send commands with authorized user"
      (testing "even handler cannot comment without request"
        (is (= {:errors [{:type "forbidden"}], :success false}
               (send-dynamic-command handler-id
                                     {:type :application.command/comment
                                      :application-id application-id
                                      :comment "What am I commenting on?"}))))
      (testing "comment with request"
        (let [eventcount (count (get (get-v2-application application-id handler-id) :events))]
          (testing "requesting comment"
            (is (= {:success true} (send-dynamic-command handler-id
                                                         {:type :application.command/request-comment
                                                          :application-id application-id
                                                          :commenters [decider-id commenter-id]
                                                          :comment "What say you?"}))))
          (testing "commenter can now comment"
            (is (= {:success true} (send-dynamic-command commenter-id
                                                         {:type :application.command/comment
                                                          :application-id application-id
                                                          :comment "Yeah, I dunno"}))))
          (testing "comment was linked to request"
            (let [application (get-v2-application application-id handler-id)
                  request-event (get-in application [:application/events eventcount])
                  comment-event (get-in application [:application/events (inc eventcount)])]
              (is (= (:application/request-id request-event)
                     (:application/request-id comment-event)))))))
      (testing "request-decision"
        (is (= {:success true} (send-dynamic-command handler-id
                                                     {:type :application.command/request-decision
                                                      :application-id application-id
                                                      :deciders [decider-id]
                                                      :comment ""}))))
      (testing "decide"
        (is (= {:success true} (send-dynamic-command decider-id
                                                     {:type :application.command/decide
                                                      :application-id application-id
                                                      :decision :approved
                                                      :comment ""}))))
      (testing "approve"
        (is (= {:success true} (send-dynamic-command handler-id {:type :application.command/approve
                                                                 :application-id application-id
                                                                 :comment ""})))
        (let [handler-data (get-v2-application application-id handler-id)
              handler-event-types (map :event/type (get handler-data :application/events))
              applicant-data (get-v2-application application-id user-id)
              applicant-event-types (map :event/type (get applicant-data :application/events))]
          (testing "handler can see all events"
            (is (= {:application/id application-id
                    :application/state "application.state/approved"}
                   (select-keys handler-data [:application/id :application/state])))
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
        application-id (create-dynamic-application user-id)]

    (testing "creating"
      (is (some? application-id))
      (let [created (get-v2-application application-id user-id)]
        (is (= "application.state/draft" (get created :application/state)))))

    (testing "getting application as other user is forbidden"
      (is (response-is-forbidden?
           (-> (request :get (str "/api/v2/applications/" application-id))
               (authenticate api-key "bob")
               handler))))

    (testing "modifying application as other user is forbidden"
      (is (= {:success false
              :errors [{:type "forbidden"}]}
             (send-dynamic-command "bob" {:type :application.command/save-draft
                                          :application-id application-id
                                          :field-values {}
                                          :accepted-licenses #{}}))))

    (testing "submitting"
      (is (= {:success true}
             (send-dynamic-command user-id {:type :application.command/submit
                                            :application-id application-id})))
      (let [submitted (get-v2-application application-id user-id)]
        (is (= "application.state/submitted" (get submitted :application/state)))
        (is (= ["application.event/created"
                "application.event/submitted"]
               (map :event/type (get submitted :application/events))))))))

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
        workflow-id (create-dynamic-workflow)
        form-id (create-form-with-fields [{:title {:en "some attachment"}
                                           :type "attachment"
                                           :optional true}])
        field-id (-> (form/get-form form-id) :fields first :id)
        cat-id (create-catalogue-item form-id workflow-id)
        app-id (create-v2-application [cat-id] user-id)]
    (testing "uploading attachment for a draft"
      (let [body (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                     (assoc :params {"file" filecontent})
                     (assoc :multipart-params {"file" filecontent})
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)]
        (is (= {:success true} body))))
    (testing "uploading malicious file for a draft"
      (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                         (assoc :params {"file" malicious-content})
                         (assoc :multipart-params {"file" malicious-content})
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-bad-request? response))))
    (testing "retrieving attachment for a draft"
      (let [response (-> (request :get "/api/applications/attachments" {:application-id app-id :field-id field-id})
                         (authenticate api-key user-id)
                         handler
                         assert-response-is-ok)]
        (is (= (slurp testfile) (slurp (:body response))))))
    (testing "uploading attachment as non-applicant"
      (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                         (assoc :params {"file" filecontent})
                         (assoc :multipart-params {"file" filecontent})
                         (authenticate api-key "carl")
                         handler)]
        (is (response-is-forbidden? response))))
    (testing "retrieving attachment as non-applicant"
      (let [response (-> (request :get "/api/applications/attachments" {:application-id app-id :field-id field-id})
                         (authenticate api-key "carl")
                         handler)]
        (is (response-is-forbidden? response))))
    (testing "submit application"
      (is (= {:success true} (send-dynamic-command user-id {:type :application.command/submit
                                                            :application-id app-id}))))
    (testing "uploading attachment for a submitted application"
      (let [response (-> (request :post (str "/api/applications/add_attachment?application-id=" app-id "&field-id=" field-id))
                         (assoc :params {"file" filecontent})
                         (assoc :multipart-params {"file" filecontent})
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-forbidden? response))))))

(deftest applications-api-security-test
  (let [cat-id (create-dymmy-catalogue-item)
        app-id (create-v2-application [cat-id] "alice")]
    (testing "fetch application without authentication"
      (is (response-is-unauthorized? (-> (request :get (str "/api/v2/applications/" app-id))
                                         handler))))
    (testing "fetch deciders without authentication"
      (is (response-is-unauthorized? (-> (request :get "/api/applications/deciders")
                                         handler))))
    (testing "create without authentication"
      (is (response-is-unauthorized? (-> (request :post "/api/v2/applications/create")
                                         (json-body {:catalogue-item-ids [cat-id]})
                                         handler))))
    (testing "create with wrong API-Key"
      (is (response-is-unauthorized? (-> (request :post "/api/v2/applications/create")
                                         (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                                         (json-body {:catalogue-item-ids [cat-id]})
                                         handler))))
    (testing "send command without authentication"
      (is (response-is-unauthorized? (-> (request :post "/api/applications/command")
                                         (json-body {:type :application.command/submit
                                                     :application-id app-id})
                                         handler))))
    (testing "send command with wrong api-key"
      (is (response-is-unauthorized? (-> (request :post "/api/applications/command")
                                         (authenticate "invalid-api-key" "alice")
                                         (json-body {:type :application.command/submit
                                                     :application-id app-id})
                                         handler))))
    (testing "upload attachment without authentication"
      (is (response-is-unauthorized? (-> (request :post "/api/applications/add_attachment")
                                         (assoc :params {"file" filecontent})
                                         (assoc :multipart-params {"file" filecontent})
                                         handler))))
    (testing "upload attachment with wrong API-Key"
      (is (response-is-unauthorized? (-> (request :post "/api/applications/add_attachment")
                                         (assoc :params {"file" filecontent})
                                         (assoc :multipart-params {"file" filecontent})
                                         (authenticate "invalid-api-key" "developer")
                                         handler))))))

(deftest test-v2-application-api
  (let [app-id (create-dynamic-application "alice")]

    (testing "list user applications"
      (is (contains? (get-ids (get-v2-applications "alice"))
                     app-id)))

    (testing "get single application"
      (is (= app-id
             (:application/id (get-v2-application app-id "alice")))))))

(deftest test-v2-review-api
  (let [app-id (create-dynamic-application "alice")]

    (testing "does not list drafts"
      (is (not (contains? (get-ids (get-v2-open-reviews "developer"))
                          app-id))))

    (testing "lists submitted in open reviews"
      (is (= {:success true} (send-dynamic-command "alice" {:type :application.command/submit
                                                            :application-id app-id})))
      (is (contains? (get-ids (get-v2-open-reviews "developer"))
                     app-id))
      (is (not (contains? (get-ids (get-v2-handled-reviews "developer"))
                          app-id))))

    (testing "lists handled in handled reviews"
      (is (= {:success true} (send-dynamic-command "developer" {:type :application.command/approve
                                                                :application-id app-id
                                                                :comment ""})))
      (is (not (contains? (get-ids (get-v2-open-reviews "developer"))
                          app-id)))
      (is (contains? (get-ids (get-v2-handled-reviews "developer"))
                     app-id)))))
