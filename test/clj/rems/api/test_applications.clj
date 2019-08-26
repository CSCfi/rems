(ns ^:integration rems.api.test-applications
  (:require [clojure.test :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.testing :refer :all]
            [rems.db.form :as form]
            [rems.db.test-data :as test-data]
            [rems.handler :refer [handler]]
            [rems.json]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

;;; shared helpers

(defn- send-command [actor cmd]
  (-> (request :post (str "/api/applications/" (name (:type cmd))))
      (authenticate "42" actor)
      (json-body (dissoc cmd :type))
      handler
      read-body))

(defn- get-ids [applications]
  (set (map :application/id applications)))

(defn- license-ids-for-application [application]
  (set (map :license/id (:application/licenses application))))

(defn- catalogue-item-ids-for-application [application]
  (set (map :catalogue-item/id (:application/resources application))))

(defn- get-my-applications [user-id & [params]]
  (-> (request :get "/api/my-applications" params)
      (authenticate "42" user-id)
      handler
      read-ok-body))

(defn- get-all-applications [user-id & [params]]
  (-> (request :get "/api/applications" params)
      (authenticate "42" user-id)
      handler
      read-ok-body))

(defn- get-application [app-id user-id]
  (-> (request :get (str "/api/applications/" app-id))
      (authenticate "42" user-id)
      handler
      read-ok-body))

(defn- get-todos [user-id & [params]]
  (-> (request :get "/api/applications/todo" params)
      (authenticate "42" user-id)
      handler
      read-ok-body))

(defn- get-handled-todos [user-id & [params]]
  (-> (request :get "/api/applications/handled" params)
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

(deftest test-application-api-session
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
        cat-id (test-data/create-catalogue-item! {})]
    (is cookie)
    (is csrf)
    (testing "save with session"
      (let [body (-> (request :post "/api/applications/create")
                     (header "Cookie" cookie)
                     (header "x-csrf-token" csrf)
                     (json-body {:catalogue-item-ids [cat-id]})
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:success body))))
    (testing "save with session but without csrf"
      (let [response (-> (request :post "/api/applications/create")
                         (header "Cookie" cookie)
                         (json-body {:catalogue-item-ids [cat-id]})
                         handler)]
        (is (response-is-unauthorized? response))))
    (testing "save with session and csrf and wrong api-key"
      (let [response (-> (request :post "/api/applications/create")
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
      (is (= "filename=\"13.pdf\"" (get-in response [:headers "Content-Disposition"])))
      (is (.startsWith (slurp (:body response)) "%PDF-1.")))))

(deftest test-application-commands
  (let [user-id "alice"
        handler-id "developer"
        commenter-id "carl"
        decider-id "bob"
        license-id1 (test-data/create-license! {})
        license-id2 (test-data/create-license! {})
        license-id3 (test-data/create-license! {})
        license-id4 (test-data/create-license! {})
        form-id (test-data/create-form! {})
        workflow-id (test-data/create-dynamic-workflow! {:handlers [handler-id]})
        cat-item-id1 (test-data/create-catalogue-item! {:resource-id (test-data/create-resource!
                                                                      {:license-ids [license-id1 license-id2]})
                                                        :form-id form-id
                                                        :workflow-id workflow-id})
        cat-item-id2 (test-data/create-catalogue-item! {:resource-id (test-data/create-resource!
                                                                      {:license-ids [license-id1 license-id2]})
                                                        :form-id form-id
                                                        :workflow-id workflow-id})

        cat-item-id3 (test-data/create-catalogue-item! {:resource-id (test-data/create-resource!
                                                                      {:license-ids [license-id3]})
                                                        :form-id form-id
                                                        :workflow-id workflow-id})
        application-id (test-data/create-application! {:catalogue-item-ids [cat-item-id1]
                                                       :actor user-id})]

    (testing "accept licenses"
      (is (= {:success true} (send-command user-id
                                           {:type :application.command/accept-licenses
                                            :application-id application-id
                                            :accepted-licenses []}))))

    (testing "save draft"
      (is (= {:success true} (send-command user-id
                                           {:type :application.command/save-draft
                                            :application-id application-id
                                            :field-values []}))))

    (testing "submit"
      (is (= {:success true} (send-command user-id
                                           {:type :application.command/submit
                                            :application-id application-id}))))

    (testing "getting dynamic application as applicant"
      (let [application (get-application application-id user-id)]
        (is (= "workflow/dynamic" (get-in application [:application/workflow :workflow/type])))
        (is (= ["application.event/created"
                "application.event/licenses-accepted"
                "application.event/draft-saved"
                "application.event/submitted"]
               (map :event/type (get application :application/events))))
        (is (= #{"application.command/remove-member"
                 "application.command/uninvite-member"
                 "application.command/accept-licenses"
                 "application.command/copy-as-new"}
               (set (get application :application/permissions))))))

    (testing "getting dynamic application as handler"
      (let [application (get-application application-id handler-id)]
        (is (= "workflow/dynamic" (get-in application [:application/workflow :workflow/type])))
        (is (= #{"application.command/request-comment"
                 "application.command/request-decision"
                 "application.command/remark"
                 "application.command/reject"
                 "application.command/approve"
                 "application.command/return"
                 "application.command/add-licenses"
                 "application.command/add-member"
                 "application.command/remove-member"
                 "application.command/invite-member"
                 "application.command/uninvite-member"
                 "application.command/change-resources"
                 "see-everything"}
               (set (get application :application/permissions))))))

    (testing "send command without user"
      (is (= {:success false
              :errors [{:type "forbidden"}]}
             (send-command "" {:type :application.command/approve
                               :application-id application-id
                               :comment ""}))
          "user should be forbidden to send command"))

    (testing "send command with a user that is not a handler"
      (is (= {:success false
              :errors [{:type "forbidden"}]}
             (send-command user-id {:type :application.command/approve
                                    :application-id application-id
                                    :comment ""}))
          "user should be forbidden to send command"))

    (testing "application can be returned"
      (is (= {:success true} (send-command handler-id
                                           {:type :application.command/return
                                            :application-id application-id
                                            :comment "Please check again"}))))

    (testing "changing resources as applicant"
      (is (= {:success true} (send-command user-id
                                           {:type :application.command/change-resources
                                            :application-id application-id
                                            :catalogue-item-ids [cat-item-id2]}))))

    (testing "submitting again"
      (is (= {:success true} (send-command user-id
                                           {:type :application.command/submit
                                            :application-id application-id}))))

    (testing "send commands with authorized user"
      (testing "even handler cannot comment without request"
        (is (= {:errors [{:type "forbidden"}], :success false}
               (send-command handler-id
                             {:type :application.command/comment
                              :application-id application-id
                              :comment "What am I commenting on?"}))))
      (testing "comment with request"
        (let [eventcount (count (get (get-application application-id handler-id) :events))]
          (testing "requesting comment"
            (is (= {:success true} (send-command handler-id
                                                 {:type :application.command/request-comment
                                                  :application-id application-id
                                                  :commenters [decider-id commenter-id]
                                                  :comment "What say you?"}))))
          (testing "commenter can now comment"
            (is (= {:success true} (send-command commenter-id
                                                 {:type :application.command/comment
                                                  :application-id application-id
                                                  :comment "Yeah, I dunno"}))))
          (testing "comment was linked to request"
            (let [application (get-application application-id handler-id)
                  request-event (get-in application [:application/events eventcount])
                  comment-event (get-in application [:application/events (inc eventcount)])]
              (is (= (:application/request-id request-event)
                     (:application/request-id comment-event)))))))

      (testing "adding and then accepting additional licenses"
        (testing "add licenses"
          (let [application (get-application application-id user-id)]
            (is (= #{license-id1 license-id2} (license-ids-for-application application)))
            (is (= {:success true} (send-command handler-id
                                                 {:type :application.command/add-licenses
                                                  :application-id application-id
                                                  :licenses [license-id4]
                                                  :comment "Please approve these new terms"})))
            (let [application (get-application application-id user-id)]
              (is (= #{license-id1 license-id2 license-id4} (license-ids-for-application application))))))
        (testing "applicant accepts the additional licenses"
          (is (= {:success true} (send-command user-id
                                               {:type :application.command/accept-licenses
                                                :application-id application-id
                                                :accepted-licenses [license-id4]})))))

      (testing "changing resources as handler"
        (let [application (get-application application-id user-id)]
          (is (= #{cat-item-id2} (catalogue-item-ids-for-application application)))
          (is (= #{license-id1 license-id2 license-id4} (license-ids-for-application application)))
          (is (= {:success true} (send-command handler-id
                                               {:type :application.command/change-resources
                                                :application-id application-id
                                                :catalogue-item-ids [cat-item-id3]
                                                :comment "Here are the correct resources"})))
          (let [application (get-application application-id user-id)]
            (is (= #{cat-item-id3} (catalogue-item-ids-for-application application)))
            ;; TODO: The previously added licenses should probably be retained in the licenses after changing resources.
            (is (= #{license-id3} (license-ids-for-application application))))))

      (testing "changing resources back as handler"
        (is (= {:success true} (send-command handler-id
                                             {:type :application.command/change-resources
                                              :application-id application-id
                                              :catalogue-item-ids [cat-item-id2]})))
        (let [application (get-application application-id user-id)]
          (is (= #{cat-item-id2} (catalogue-item-ids-for-application application)))
          (is (= #{license-id1 license-id2} (license-ids-for-application application)))))

      (testing "request-decision"
        (is (= {:success true} (send-command handler-id
                                             {:type :application.command/request-decision
                                              :application-id application-id
                                              :deciders [decider-id]
                                              :comment ""}))))
      (testing "decide"
        (is (= {:success true} (send-command decider-id
                                             {:type :application.command/decide
                                              :application-id application-id
                                              :decision :approved
                                              :comment ""}))))
      (testing "hidden remark"
        (is (= {:success true} (send-command handler-id {:type :application.command/remark
                                                         :application-id application-id
                                                         :comment ""
                                                         :public false}))))
      (testing "public remark"
        (is (= {:success true} (send-command handler-id {:type :application.command/remark
                                                         :application-id application-id
                                                         :comment ""
                                                         :public true}))))
      (testing "approve"
        (is (= {:success true} (send-command handler-id {:type :application.command/approve
                                                         :application-id application-id
                                                         :comment ""})))
        (let [handler-data (get-application application-id handler-id)
              handler-event-types (map :event/type (get handler-data :application/events))
              applicant-data (get-application application-id user-id)
              applicant-event-types (map :event/type (get applicant-data :application/events))]
          (testing "handler can see all events"
            (is (= {:application/id application-id
                    :application/state "application.state/approved"}
                   (select-keys handler-data [:application/id :application/state])))
            (is (= ["application.event/created"
                    "application.event/licenses-accepted"
                    "application.event/draft-saved"
                    "application.event/submitted"
                    "application.event/returned"
                    "application.event/resources-changed"
                    "application.event/submitted"
                    "application.event/comment-requested"
                    "application.event/commented"
                    "application.event/licenses-added"
                    "application.event/licenses-accepted"
                    "application.event/resources-changed"
                    "application.event/resources-changed"
                    "application.event/decision-requested"
                    "application.event/decided"
                    "application.event/remarked"
                    "application.event/remarked"
                    "application.event/approved"]
                   handler-event-types)))
          (testing "applicant cannot see all events"
            (is (= ["application.event/created"
                    "application.event/licenses-accepted"
                    "application.event/draft-saved"
                    "application.event/submitted"
                    "application.event/returned"
                    "application.event/resources-changed"
                    "application.event/submitted"
                    "application.event/licenses-added"
                    "application.event/licenses-accepted"
                    "application.event/resources-changed"
                    "application.event/resources-changed"
                    "application.event/remarked"
                    "application.event/approved"]
                   applicant-event-types)))))

      (testing "copy as new"
        (let [result (send-command user-id {:type :application.command/copy-as-new
                                            :application-id application-id})]
          (is (:success result)
              {:result result})
          (is (:application-id result)
              {:result result})
          (is (not= application-id (:application-id result))
              "should create a new application"))))))

(deftest test-application-create
  (let [api-key "42"
        user-id "alice"
        application-id (-> (request :post "/api/applications/create")
                           (authenticate "42" user-id)
                           (json-body {:catalogue-item-ids [(test-data/create-catalogue-item! {})]})
                           handler
                           read-ok-body
                           :application-id)]

    (testing "creating"
      (is (some? application-id))
      (let [created (get-application application-id user-id)]
        (is (= "application.state/draft" (get created :application/state)))))

    (testing "getting application as other user is forbidden"
      (is (response-is-forbidden?
           (-> (request :get (str "/api/applications/" application-id))
               (authenticate api-key "bob")
               handler))))

    (testing "modifying application as other user is forbidden"
      (is (= {:success false
              :errors [{:type "forbidden"}]}
             (send-command "bob" {:type :application.command/save-draft
                                  :application-id application-id
                                  :field-values []}))))
    (testing "submitting"
      (is (= {:success true}
             (send-command user-id {:type :application.command/submit
                                    :application-id application-id})))
      (let [submitted (get-application application-id user-id)]
        (is (= "application.state/submitted" (get submitted :application/state)))
        (is (= ["application.event/created"
                "application.event/submitted"]
               (map :event/type (get submitted :application/events))))))))

(deftest test-application-close
  (let [user-id "alice"
        application-id (test-data/create-application! {:actor user-id})]
    (is (= {:success true}
           (send-command user-id {:type :application.command/close
                                  :application-id application-id
                                  :comment ""})))
    (is (= "application.state/closed"
           (:application/state (get-application application-id user-id))))))

(deftest test-application-submit
  (let [user-id "alice"
        form-id (test-data/create-form! {})
        cat-id (test-data/create-catalogue-item! {:form-id form-id})
        app-id (test-data/create-application! {:catalogue-item-ds [cat-id] :actor user-id})]
    (testing "submit with disabled catalogue item fails"
      (is (:success (catalogue/update-catalogue-item! {:id cat-id
                                                       :enabled false
                                                       :archived false})))
      (is (= {:success false}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))
    (testing "submit with archived catalogue item fails"
      (is (:success (catalogue/update-catalogue-item! {:id cat-id
                                                       :enabled true
                                                       :archived true})))
      (is (= {:success false}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))
    (testing "submit with normal catalogue item succeeds"
      (is (:success (catalogue/update-catalogue-item! {:id cat-id
                                                       :enabled true
                                                       :archived false})))
      (is (= {:success true}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))))

(deftest test-application-validation
  (let [user-id "alice"
        form-id (test-data/create-form! {:form/fields [{:field/title {:en "req"}
                                                        :field/type :text
                                                        :field/optional false}
                                                       {:field/title {:en "opt"}
                                                        :field/type :text
                                                        :field/optional true}]})
        [req-id opt-id] (->> (form/get-form-template form-id)
                             :form/fields
                             (map :field/id))
        cat-id (test-data/create-catalogue-item! {:form-id form-id})
        app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                               :actor user-id})]

    (testing "set value of optional field"
      (is (= {:success true}
             (send-command user-id {:type :application.command/save-draft
                                    :application-id app-id
                                    :field-values [{:field opt-id :value "opt"}]}))))
    (testing "can't submit without required field"
      (is (= {:success false
              :errors [{:field-id req-id, :type "t.form.validation/required"}]}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))
    (testing "set value of required field"
      (is (= {:success true}
             (send-command user-id {:type :application.command/save-draft
                                    :application-id app-id
                                    :field-values [{:field opt-id :value "opt"}
                                                   {:field req-id :value "req"}]}))))
    (testing "can submit with required field"
      (is (= {:success true}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))))

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

(deftest test-application-api-attachments
  (let [api-key "42"
        user-id "alice"
        form-id (test-data/create-form! {:form/fields [{:field/title {:en "some attachment"}
                                                        :field/type :attachment
                                                        :field/optional true}]})
        cat-id (test-data/create-catalogue-item! {:form-id form-id})
        app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                               :actor user-id})
        upload-request (fn [file]
                         (-> (request :post (str "/api/applications/add-attachment?application-id=" app-id))
                             (assoc :params {"file" file})
                             (assoc :multipart-params {"file" file})))
        read-request #(request :get (str "/api/applications/attachment/" %))]
    (testing "uploading attachment for a draft"
      (let [body (-> (upload-request filecontent)
                     (authenticate api-key user-id)
                     handler
                     read-ok-body)
            id (:id body)]
        (is (:success body))
        (is (number? id))
        (testing "and retrieving it as the applicant"
          (let [response (-> (read-request id)
                             (authenticate api-key user-id)
                             handler
                             assert-response-is-ok)]
            (is (= (slurp testfile) (slurp (:body response))))))
        (testing "and retrieving it as non-applicant"
          (let [response (-> (read-request id)
                             (authenticate api-key "carl")
                             handler)]
            (is (response-is-forbidden? response))))))
    (testing "retrieving nonexistent attachment"
      (let [response (-> (read-request 999999999999999)
                         (authenticate api-key "carl")
                         handler)]
        (is (response-is-not-found? response))))
    (testing "uploading attachment for nonexistent application"
      (let [response (-> (request :post "/api/applications/add-attachment?application-id=99999999")
                         (assoc :params {"file" filecontent})
                         (assoc :multipart-params {"file" filecontent})
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-forbidden? response))))
    (testing "uploading malicious file for a draft"
      (let [response (-> (upload-request malicious-content)
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-bad-request? response))))
    (testing "uploading attachment without authentication"
      (let [response (-> (upload-request filecontent)
                         handler)]
        (is (response-is-unauthorized? response))))
    (testing "uploading attachment with wrong API key"
      (let [response (-> (upload-request filecontent)
                         (authenticate api-key user-id)
                         (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                         handler)]
        (is (response-is-unauthorized? response))))
    (testing "uploading attachment as non-applicant"
      (let [response (-> (upload-request filecontent)
                         (authenticate api-key "carl")
                         handler)]
        (is (response-is-forbidden? response))))
    (testing "uploading attachment for a submitted application"
      (assert (= {:success true} (send-command user-id {:type :application.command/submit
                                                        :application-id app-id})))
      (let [response (-> (upload-request filecontent)
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-forbidden? response))))))

(deftest test-applications-api-security
  (let [api-key "42"
        applicant "alice"
        cat-id (test-data/create-catalogue-item! {})
        app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                               :actor applicant})]

    (testing "fetch application without authentication"
      (let [req (request :get (str "/api/applications/" app-id))]
        (assert-response-is-ok (-> req
                                   (authenticate api-key applicant)
                                   handler))
        (is (response-is-unauthorized? (-> req
                                           handler)))))

    (testing "fetch nonexistent application"
      (let [response (-> (request :get "/api/applications/9999999999")
                         (authenticate api-key applicant)
                         handler)]
        (is (response-is-not-found? response))
        (is (= "application/json" (get-in response [:headers "Content-Type"])))
        (is (= {:error "not found"} (rems.json/parse-string (read-body response))))))

    (testing "fetch deciders without authentication or as non-handler"
      (let [req (request :get "/api/applications/deciders")]
        (assert-response-is-ok (-> req
                                   (authenticate api-key "developer")
                                   handler))
        (is (response-is-forbidden? (-> req
                                        (authenticate api-key applicant)
                                        handler)))
        (is (response-is-unauthorized? (-> req
                                           handler)))))

    (testing "create without authentication"
      (let [req (-> (request :post "/api/applications/create")
                    (json-body {:catalogue-item-ids [cat-id]}))]
        (assert-response-is-ok (-> req
                                   (authenticate api-key applicant)
                                   handler))
        (is (response-is-unauthorized? (-> req
                                           handler)))))

    (testing "create with wrong API key"
      (let [req (-> (request :post "/api/applications/create")
                    (authenticate api-key applicant)
                    (json-body {:catalogue-item-ids [cat-id]}))]
        (assert-response-is-ok (-> req
                                   handler))
        (is (response-is-unauthorized? (-> req
                                           (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                                           handler)))))

    (testing "send command without authentication"
      (let [req (-> (request :post "/api/applications/submit")
                    (json-body {:application-id app-id}))]
        (assert-response-is-ok (-> req
                                   (authenticate api-key applicant)
                                   handler))
        (is (response-is-unauthorized? (-> req
                                           handler)))))

    (testing "send command with wrong API key"
      (let [req (-> (request :post "/api/applications/submit")
                    (authenticate api-key applicant)
                    (json-body {:application-id app-id}))]
        (assert-response-is-ok (-> req
                                   handler))
        (is (response-is-unauthorized? (-> req
                                           (assoc-in [:headers "x-rems-api-key"] "invalid-api-key")
                                           handler)))))))

(deftest test-application-listing
  (let [app-id (test-data/create-application! {:actor "alice"})]

    (testing "list user applications"
      (is (contains? (get-ids (get-my-applications "alice"))
                     app-id)))

    (testing "search user applications"
      (is (contains? (get-ids (get-my-applications "alice" {:query "applicant:alice"}))
                     app-id))
      (is (empty? (get-ids (get-my-applications "alice" {:query "applicant:no-such-user"})))))

    (testing "list all applications"
      (is (contains? (get-ids (get-all-applications "alice"))
                     app-id)))

    (testing "search all applications"
      (is (contains? (get-ids (get-all-applications "alice" {:query "applicant:alice"}))
                     app-id))
      (is (empty? (get-ids (get-all-applications "alice" {:query "applicant:no-such-user"})))))))

(deftest test-todos
  (let [app-id (test-data/create-application! {:actor "alice"})]

    (testing "does not list drafts"
      (is (not (contains? (get-ids (get-todos "developer"))
                          app-id))))

    (testing "lists submitted in todos"
      (is (= {:success true} (send-command "alice" {:type :application.command/submit
                                                    :application-id app-id})))
      (is (contains? (get-ids (get-todos "developer"))
                     app-id))
      (is (not (contains? (get-ids (get-handled-todos "developer"))
                          app-id))))

    (testing "search todos"
      (is (contains? (get-ids (get-todos "developer" {:query "applicant:alice"}))
                     app-id))
      (is (empty? (get-ids (get-todos "developer" {:query "applicant:no-such-user"})))))

    (testing "commenter sees application in todos"
      (is (= {:success true} (send-command "developer" {:type :application.command/request-comment
                                                        :application-id app-id
                                                        :commenters ["bob"]
                                                        :comment "x"})))
      (is (contains? (get-ids (get-todos "bob"))
                     app-id))
      (is (not (contains? (get-ids (get-handled-todos "bob"))
                          app-id))))

    (testing "lists handled in handled"
      (is (= {:success true} (send-command "developer" {:type :application.command/approve
                                                        :application-id app-id
                                                        :comment ""})))
      (is (not (contains? (get-ids (get-todos "developer"))
                          app-id)))
      (is (contains? (get-ids (get-handled-todos "developer"))
                     app-id)))

    (testing "search handled todos"
      (is (contains? (get-ids (get-handled-todos "developer" {:query "applicant:alice"}))
                     app-id))
      (is (empty? (get-ids (get-handled-todos "developer" {:query "applicant:no-such-user"})))))

    (testing "commenter doesn't see accepted application in todos"
      (is (not (contains? (get-ids (get-todos "bob"))
                          app-id)))
      (is (contains? (get-ids (get-handled-todos "bob"))
                     app-id)))))
