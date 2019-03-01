(ns ^:integration rems.test.api.applications
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.db.test-data :as test-data]
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
          (is (= [1 2 3 4 5 6 7 19] (map :id (sort-by :id data))))))
      (testing "transit support"
        (let [response (-> (request :get "/api/applications")
                           (authenticate api-key user-id)
                           (header "Accept" "application/transit+json")
                           app
                           assert-response-is-ok)
              data (read-body response)]
          (is (= "application/transit+json; charset=utf-8" (get-in response [:headers "Content-Type"])))
          (is (= 8 (count data))))))))

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
                  :wfid 1
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
        commenter-id "carl"
        decider-id "bob"
        application-id 13] ;; submitted dynamic application from test data

    (testing "getting dynamic application as applicant"
      (let [data (get-application user-id application-id)]
        (is (= "workflow/dynamic" (get-in data [:application :workflow :type])))
        (is (= ["application.event/created"
                "application.event/draft-saved"
                "application.event/submitted"]
               (map :event/type (get-in data [:application :dynamic-events]))))
        (is (= ["rems.workflow.dynamic/accept-invitation"
                "rems.workflow.dynamic/remove-member"
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
                 "rems.workflow.dynamic/accept-invitation"
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
                                                          :comment "What say you?"})))
            (is (= #{commenter-id decider-id}
                   (set (get-in (get-application handler-id application-id)
                                [:application :commenters])))))
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
                                                      :comment ""})))
        (let [data (get-application handler-id application-id)]
          (is (= {:id application-id
                  :deciders [decider-id]
                  :state "rems.workflow.dynamic/submitted"}
                 (select-keys (:application data) [:id :deciders :state])))))
      (testing "decide"
        (is (= {:success true} (send-dynamic-command decider-id
                                                     {:type :rems.workflow.dynamic/decide
                                                      :application-id application-id
                                                      :decision :approved
                                                      :comment ""})))
        (let [data (get-application handler-id application-id)]
          (is (= {:id application-id
                  :deciders []
                  :state "rems.workflow.dynamic/submitted"}
                 (select-keys (:application data) [:id :deciders :state])))))
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
             (-> (request :get (str "/api/applications/" application-id))
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
        user-id "alice"
        catid 11
        application-id 19]
    (testing "save draft for disabled item"
      (let [response (-> (request :post (str "/api/applications/save"))
                         (authenticate api-key user-id)
                         (json-body {:command "save"
                                     :catalogue-items [catid]
                                     :items {1 ""}})
                         app)]
        (is (= 400 (:status response)))))
    (testing "submit for application with disabled item"
      (is (= {:success false :errors [{:type "forbidden"}]}
             (send-dynamic-command user-id {:type :rems.workflow.dynamic/submit
                                            :application-id application-id}))))))

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
  (testing "listing without authentication"
    (is (response-is-unauthorized? (-> (request :get (str "/api/applications"))
                                       app))))
  (testing "fetch application without authentication"
    (is (response-is-unauthorized? (-> (request :get (str "/api/applications/1"))
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
