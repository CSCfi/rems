(ns ^:integration rems.api.test-applications
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.testing :refer :all]
            [rems.db.applications]
            [rems.db.blacklist :as blacklist]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.test-data :as test-data]
            [rems.handler :refer [handler]]
            [rems.json]
            [rems.testing-util :refer [with-user]]
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

(deftest test-application-api-session
  (let [username "alice"
        cookie (login-with-cookies username)
        csrf (get-csrf-token cookie)
        cat-id (test-data/create-catalogue-item! {})]
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
      (let [body (-> (request :post "/api/applications/create")
                     (header "Cookie" cookie)
                     (header "x-csrf-token" csrf)
                     (header "x-rems-api-key" "WRONG")
                     (json-body {:catalogue-item-ids [cat-id]})
                     handler
                     assert-response-is-ok
                     read-body)]
        (is (:success body))))))

(deftest pdf-smoke-test
  (testing "not found"
    (let [response (-> (request :get "/api/applications/9999999/pdf")
                       (authenticate "42" "developer")
                       handler)]
      (is (response-is-not-found? response))))
  (let [cat-id (test-data/create-catalogue-item! {:title {:fi "Fi title" :en "En title"}})
        application-id (test-data/create-application! {:actor "alice"
                                                       :catalogue-item-ids [cat-id]})]
    (test-data/command! {:type :application.command/submit
                         :application-id application-id
                         :actor "alice"})
    (testing "forbidden"
      (let [response (-> (request :get (str "/api/applications/" application-id "/pdf"))
                         (authenticate "42" "bob")
                         handler)]
        (is (response-is-forbidden? response))))
    (testing "success"
      (let [response (-> (request :get (str "/api/applications/" application-id "/pdf"))
                         (authenticate "42" "developer")
                         handler
                         assert-response-is-ok)]
        (is (= "application/pdf" (get-in response [:headers "Content-Type"])))
        (is (= (str "filename=\"" application-id ".pdf\"")
               (get-in response [:headers "Content-Disposition"])))
        (is (.startsWith (slurp (:body response)) "%PDF-1."))))))

(deftest test-application-commands
  (let [user-id "alice"
        handler-id "developer"
        reviewer-id "carl"
        decider-id "elsa"
        license-id1 (test-data/create-license! {})
        license-id2 (test-data/create-license! {})
        license-id3 (test-data/create-license! {})
        license-id4 (test-data/create-license! {})
        form-id (test-data/create-form! {})
        workflow-id (test-data/create-workflow! {:type :workflow/master
                                                 :handlers [handler-id]})
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
                                            :accepted-licenses [license-id1 license-id2]}))))

    (testing "save draft"
      (is (= {:success true} (send-command user-id
                                           {:type :application.command/save-draft
                                            :application-id application-id
                                            :field-values []}))))

    (testing "submit"
      (is (= {:success true} (send-command user-id
                                           {:type :application.command/submit
                                            :application-id application-id}))))

    (testing "getting application as applicant"
      (let [application (get-application application-id user-id)]
        (is (= "workflow/master" (get-in application [:application/workflow :workflow/type])))
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

    (testing "getting application as handler"
      (let [application (get-application application-id handler-id)]
        (is (= "workflow/master" (get-in application [:application/workflow :workflow/type])))
        (is (= #{"application.command/request-review"
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
                 "application.command/close"
                 "application.command/assign-external-id"
                 "see-everything"}
               (set (get application :application/permissions))))))

    (testing "disabling a command"
      (with-redefs [rems.config/env (assoc rems.config/env :disable-commands [:application.command/remark])]
        (testing "handler doesn't see hidden command"
          (let [application (get-application application-id handler-id)]
            (is (= "workflow/master" (get-in application [:application/workflow :workflow/type])))
            (is (= #{"application.command/request-review"
                     "application.command/request-decision"
                     "application.command/reject"
                     "application.command/approve"
                     "application.command/return"
                     "application.command/add-licenses"
                     "application.command/add-member"
                     "application.command/remove-member"
                     "application.command/invite-member"
                     "application.command/uninvite-member"
                     "application.command/change-resources"
                     "application.command/close"
                     "application.command/assign-external-id"
                     "see-everything"}
                   (set (get application :application/permissions))))))
        (testing "disabled command fails"
          (is (= {:success false
                  :errors [{:type "forbidden"}]}
                 (send-command handler-id
                               {:type :application.command/remark
                                :application-id application-id
                                :public false
                                :comment "this is a remark"}))))))

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

    (testing "assing external id"
      (is (= {:success true} (send-command handler-id
                                           {:type :application.command/assign-external-id
                                            :application-id application-id
                                            :external-id "abc123"})))
      (let [application (get-application application-id handler-id)]
        (is (= "abc123" (:application/external-id application)))))

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
      (testing "even handler cannot review without request"
        (is (= {:errors [{:type "forbidden"}], :success false}
               (send-command handler-id
                             {:type :application.command/review
                              :application-id application-id
                              :comment "What am I commenting on?"}))))
      (testing "review with request"
        (let [eventcount (count (get (get-application application-id handler-id) :events))]
          (testing "requesting review"
            (is (= {:success true} (send-command handler-id
                                                 {:type :application.command/request-review
                                                  :application-id application-id
                                                  :reviewers [decider-id reviewer-id]
                                                  :comment "What say you?"}))))
          (testing "reviewer can now review"
            (is (= {:success true} (send-command reviewer-id
                                                 {:type :application.command/review
                                                  :application-id application-id
                                                  :comment "Yeah, I dunno"}))))
          (testing "review was linked to request"
            (let [application (get-application application-id handler-id)
                  request-event (get-in application [:application/events eventcount])
                  review-event (get-in application [:application/events (inc eventcount)])]
              (is (= (:application/request-id request-event)
                     (:application/request-id review-event)))))))

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
      (testing "public remark with"
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
                    "application.event/external-id-assigned"
                    "application.event/returned"
                    "application.event/resources-changed"
                    "application.event/submitted"
                    "application.event/review-requested"
                    "application.event/reviewed"
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
                    "application.event/external-id-assigned"
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
  (let [owner "owner"
        user-id "alice"
        form-id (test-data/create-form! {})
        cat-id (test-data/create-catalogue-item! {:form-id form-id})
        app-id (test-data/create-application! {:catalogue-item-ids [cat-id] :actor user-id})
        enable-catalogue-item! #(with-user owner
                                  (catalogue/set-catalogue-item-enabled! {:id cat-id
                                                                          :enabled %}))
        archive-catalogue-item! #(with-user owner
                                   (catalogue/set-catalogue-item-archived! {:id cat-id
                                                                            :archived %}))]
    (testing "submit with disabled catalogue item fails"
      (is (:success (enable-catalogue-item! false)))
      (rems.db.applications/reload-cache!)
      (is (= {:success false
              :errors [{:type "t.actions.errors/disabled-catalogue-item" :catalogue-item-id cat-id}]}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))
    (testing "submit with archived catalogue item fails"
      (is (:success (enable-catalogue-item! true)))
      (is (:success (archive-catalogue-item! true)))
      (rems.db.applications/reload-cache!)
      (is (= {:success false
              :errors [{:type "t.actions.errors/disabled-catalogue-item" :catalogue-item-id cat-id}]}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))
    (testing "submit with normal catalogue item succeeds"
      (is (:success (enable-catalogue-item! true)))
      (is (:success (archive-catalogue-item! false)))
      (rems.db.applications/reload-cache!)
      (is (= {:success true}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))))

(deftest test-application-validation
  (let [user-id "alice"
        form-id (test-data/create-form! {:form/fields [{:field/id "req1"
                                                        :field/title {:en "req"
                                                                      :fi "pak"}
                                                        :field/type :text
                                                        :field/optional false}
                                                       {:field/id "opt1"
                                                        :field/title {:en "opt"
                                                                      :fi "val"}
                                                        :field/type :text
                                                        :field/optional true}]})
        form-id2 (test-data/create-form! {:form/fields [{:field/id "req2"
                                                         :field/title {:en "req"
                                                                       :fi "pak"}
                                                         :field/type :text
                                                         :field/optional false}
                                                        {:field/id "opt2"
                                                         :field/title {:en "opt"
                                                                       :fi "val"}
                                                         :field/type :text
                                                         :field/optional true}]})
        wf-id (test-data/create-workflow! {})
        cat-id (test-data/create-catalogue-item! {:form-id form-id :workflow-id wf-id})
        cat-id2 (test-data/create-catalogue-item! {:form-id form-id2 :workflow-id wf-id})
        app-id (test-data/create-application! {:catalogue-item-ids [cat-id cat-id2]
                                               :actor user-id})]

    (testing "set value of optional field"
      (is (= {:success true}
             (send-command user-id {:type :application.command/save-draft
                                    :application-id app-id
                                    :field-values [{:form form-id :field "opt1" :value "opt"}]})
             (send-command user-id {:type :application.command/save-draft
                                    :application-id app-id
                                    :field-values [{:form form-id2 :field "opt2" :value "opt"}]}))))
    (testing "can't submit without required field"
      (is (= {:success false
              :errors [{:form-id form-id :field-id "req1" :type "t.form.validation/required"}
                       {:form-id form-id2 :field-id "req2" :type "t.form.validation/required"}]}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))
    (testing "set value of required field"
      (is (= {:success true}
             (send-command user-id {:type :application.command/save-draft
                                    :application-id app-id
                                    :field-values [{:form form-id :field "opt1" :value "opt"}
                                                   {:form form-id :field "req1" :value "req"}
                                                   {:form form-id2 :field "opt2" :value "opt"}
                                                   {:form form-id2 :field "req2" :value "req"}]}))))
    (testing "can submit with required field"
      (is (= {:success true}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))))

(deftest test-decider-workflow
  (let [applicant "alice"
        handler "handler"
        decider "carl"
        wf-id (test-data/create-workflow! {:type :workflow/decider
                                           :handlers [handler]})
        cat-id (test-data/create-catalogue-item! {:workflow-id wf-id})
        app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                               :actor applicant})]
    (testing "applicant's commands for draft"
      (is (= #{"application.command/accept-licenses"
               "application.command/change-resources"
               "application.command/close"
               "application.command/copy-as-new"
               "application.command/invite-member"
               "application.command/remove-member"
               "application.command/save-draft"
               "application.command/submit"
               "application.command/uninvite-member"}
             (set (:application/permissions (get-application app-id applicant))))))
    (testing "submit"
      (is (= {:success true}
             (send-command applicant {:type :application.command/submit
                                      :application-id app-id}))))
    (testing "applicant's commands after submit"
      (is (= #{"application.command/accept-licenses"
               "application.command/copy-as-new"
               "application.command/remove-member"
               "application.command/uninvite-member"}
             (set (:application/permissions (get-application app-id applicant))))))
    (testing "handler's commands"
      (is (= #{"application.command/add-licenses"
               "application.command/add-member"
               "application.command/assign-external-id"
               "application.command/change-resources"
               "application.command/close"
               "application.command/invite-member"
               "application.command/remark"
               "application.command/remove-member"
               "application.command/request-decision"
               "application.command/request-review"
               "application.command/return"
               "application.command/uninvite-member"
               "see-everything"}
             (set (:application/permissions (get-application app-id handler))))))
    (testing "request decision"
      (is (= {:success true}
             (send-command handler {:type :application.command/request-decision
                                    :application-id app-id
                                    :deciders [decider]
                                    :comment ""}))))
    (testing "decider's commands"
      (is (= #{"application.command/approve"
               "application.command/reject"
               "application.command/remark"
               "see-everything"}
             (set (:application/permissions (get-application app-id decider))))))
    (testing "approve"
      (is (= {:success true}
             (send-command decider {:type :application.command/approve
                                    :application-id app-id
                                    :comment ""}))))))

(deftest test-revoke
  (let [applicant-id "alice"
        member-id "malice"
        handler-id "handler"
        wfid (test-data/create-workflow! {:handlers [handler-id]})
        formid (test-data/create-form! {})
        ext1 "revoke-test-resource-1"
        ext2 "revoke-test-resource-2"
        res1 (test-data/create-resource! {:resource-ext-id ext1})
        res2 (test-data/create-resource! {:resource-ext-id ext2})
        cat1 (test-data/create-catalogue-item! {:workflow-id wfid :form-id formid :resource-id res1})
        cat2 (test-data/create-catalogue-item! {:workflow-id wfid :form-id formid :resource-id res2})
        app-id (test-data/create-application! {:actor applicant-id :catalogue-item-ids [cat1 cat2]})]
    (testing "set up application with multiple resources and members"
      (is (= {:success true}
             (send-command applicant-id {:type :application.command/submit
                                         :application-id app-id})))
      (is (= {:success true}
             (send-command handler-id {:type :application.command/add-member
                                       :application-id app-id
                                       :member {:userid member-id}})))
      (is (= {:success true}
             (send-command handler-id {:type :application.command/approve
                                       :application-id app-id
                                       :comment ""}))))
    (testing "entitlements are present"
      (is (= #{{:end nil :resid ext1 :userid applicant-id}
               {:end nil :resid ext2 :userid applicant-id}
               {:end nil :resid ext1 :userid member-id}
               {:end nil :resid ext2 :userid member-id}}
             (set (map #(select-keys % [:end :resid :userid])
                       (db/get-entitlements {:application app-id}))))))
    (testing "users are not blacklisted"
      (is (not (blacklist/blacklisted? applicant-id ext1)))
      (is (not (blacklist/blacklisted? applicant-id ext2)))
      (is (not (blacklist/blacklisted? member-id ext1)))
      (is (not (blacklist/blacklisted? member-id ext2))))
    (testing "revoke application"
      (is (= {:success true}
             (send-command handler-id {:type :application.command/revoke
                                       :application-id app-id
                                       :comment "bad"}))))
    (testing "entitlements end"
      (is (every? :end (db/get-entitlements {:application app-id}))))
    (testing "users are blacklisted"
      (is (blacklist/blacklisted? applicant-id ext1))
      (is (blacklist/blacklisted? applicant-id ext2))
      (is (blacklist/blacklisted? member-id ext1))
      (is (blacklist/blacklisted? member-id ext2)))))

(deftest test-application-export
  (let [applicant "alice"
        owner "owner"
        api-key "42"
        form-id (test-data/create-form! {})
        cat-id (test-data/create-catalogue-item! {:form-id form-id})
        app-id (test-data/create-application! {:actor applicant
                                               :catalogue-item-ids [cat-id]})]
    (send-command applicant {:type :application.command/submit
                             :application-id app-id})
    (let [exported (-> (request :get (str "/api/applications/export?form-id=" form-id))
                       (authenticate api-key owner)
                       handler
                       read-ok-body)]
      (is (= (count (str/split exported #"\n")) 2)))))

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
        handler-id "developer" ;; developer is the default handler in test-data
        form-id (test-data/create-form! {:form/fields [{:field/id "attach"
                                                        :field/title {:en "some attachment"
                                                                      :fi "joku liite"}
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
    (testing "uploading malicious file for a draft"
      (let [response (-> (upload-request malicious-content)
                         (authenticate api-key user-id)
                         handler)]
        (is (response-is-unsupported-media-type? response))))
    (testing "uploading attachment for a draft as handler"
      (let [response (-> (upload-request filecontent)
                         (authenticate api-key handler-id)
                         handler)]
        (is (response-is-forbidden? response))))
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
            (is (= "attachment;filename=\"test.txt\"" (get-in response [:headers "Content-Disposition"])))
            (is (= (slurp testfile) (slurp (:body response))))))
        (testing "and retrieving it as non-applicant"
          (let [response (-> (read-request id)
                             (authenticate api-key "carl")
                             handler)]
            (is (response-is-forbidden? response))))
        (testing "and using it in a field"
          (is (= {:success true}
                 (send-command user-id {:type :application.command/save-draft
                                        :application-id app-id
                                        :field-values [{:form form-id :field "attach" :value (str id)}]}))))
        (testing "and submitting"
          (is (= {:success true}
                 (send-command user-id {:type :application.command/submit
                                        :application-id app-id})))
          (testing "and accessing it as handler"
            (let [response (-> (read-request id)
                               (authenticate api-key handler-id)
                               handler
                               assert-response-is-ok)]
              (is (= "attachment;filename=\"test.txt\"" (get-in response [:headers "Content-Disposition"])))
              (is (= (slurp testfile) (slurp (:body response)))))))
        (testing "and copying the application"
          (let [response (send-command user-id {:type :application.command/copy-as-new
                                                :application-id app-id})
                new-app-id (:application-id response)]
            (is (:success response))
            (is (number? new-app-id))
            (testing "and fetching the copied attachent"
              (let [new-app (get-application new-app-id user-id)
                    new-id (get-in new-app [:application/attachments 0 :attachment/id])]
                (is (number? new-id))
                (is (not= id new-id))
                (let [response (-> (read-request new-id)
                                   (authenticate api-key user-id)
                                   handler
                                   assert-response-is-ok)]
                  (is (= "attachment;filename=\"test.txt\"" (get-in response [:headers "Content-Disposition"])))
                  (is (= (slurp testfile) (slurp (:body response)))))))))))
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
        (is (response-is-forbidden? response))))))

(deftest test-application-comment-attachments
  (let [api-key "42"
        applicant-id "alice"
        handler-id "developer"
        reviewer-id "carl"
        file #(assoc filecontent :filename %)
        workflow-id (test-data/create-workflow! {:type :workflow/master
                                                 :handlers [handler-id]})
        cat-item-id (test-data/create-catalogue-item! {:workflow-id workflow-id})
        application-id (test-data/create-application! {:catalogue-item-ids [cat-item-id]
                                                       :actor applicant-id})
        add-attachment #(-> (request :post (str "/api/applications/add-attachment?application-id=" application-id))
                            (authenticate api-key %1)
                            (assoc :params {"file" %2})
                            (assoc :multipart-params {"file" %2})
                            handler
                            read-ok-body
                            :id)]
    (testing "submit"
      (is (= {:success true} (send-command applicant-id
                                           {:type :application.command/submit
                                            :application-id application-id}))))
    (testing "unrelated user can't upload attachment"
      (is (response-is-forbidden? (-> (request :post (str "/api/applications/add-attachment?application-id=" application-id))
                                      (authenticate api-key reviewer-id)
                                      (assoc :params {"file" filecontent})
                                      (assoc :multipart-params {"file" filecontent})
                                      handler))))
    (testing "invite reviewer"
      (is (= {:success true} (send-command handler-id
                                           {:type :application.command/request-review
                                            :application-id application-id
                                            :reviewers [reviewer-id]
                                            :comment "please"}))))

    (testing "handler uploads an attachment"
      (let [attachment-id (add-attachment handler-id (file "handler-public-remark.txt"))]
        (is (number? attachment-id))
        (testing "and attaches it to a public remark"
          (is (= {:success true} (send-command handler-id
                                               {:type :application.command/remark
                                                :application-id application-id
                                                :comment "see attachment"
                                                :public true
                                                :attachments [{:attachment/id attachment-id}]}))))))

    (testing "applicant can see attachment"
      (let [app (get-application application-id applicant-id)
            remark-event (last (:application/events app))
            attachment-id (:attachment/id (first (:event/attachments remark-event)))]
        (is (number? attachment-id))
        (testing "and fetch it"
          (is (= (slurp testfile)
                 (-> (api-response :get (str "/api/applications/attachment/" attachment-id) nil
                                   api-key applicant-id)
                     assert-response-is-ok
                     :body
                     slurp))))))

    (testing "reviewer uploads an attachment"
      (let [attachment-id (add-attachment reviewer-id (file "reviewer-review.txt"))]
        (is (number? attachment-id))
        (testing ", handler can't use the attachment"
          (is (= {:success false
                  :errors [{:type "invalid-attachments" :attachments [attachment-id]}]}
                 (send-command handler-id
                               {:type :application.command/remark
                                :public false
                                :application-id application-id
                                :comment "see attachment"
                                :attachments [{:attachment/id attachment-id}]}))))
        (testing ", attaches it to a review"
          (is (= {:success true} (send-command reviewer-id
                                               {:type :application.command/review
                                                :application-id application-id
                                                :comment "see attachment"
                                                :attachments [{:attachment/id attachment-id}]})))
          (testing ", handler can fetch attachment"
            (is (= (slurp testfile)
                   (-> (api-response :get (str "/api/applications/attachment/" attachment-id) nil
                                     api-key handler-id)
                       assert-response-is-ok
                       :body
                       slurp))))
          (testing ", applicant can't fetch attachment"
            (is (response-is-forbidden? (api-response :get (str "/api/applications/attachment/" attachment-id) nil
                                                      api-key applicant-id)))))))

    (testing "handler makes a private remark"
      (let [attachment-id (add-attachment handler-id (file "handler-private-remark.txt"))]
        (is (number? attachment-id))
        (is (= {:success true} (send-command handler-id
                                             {:type :application.command/remark
                                              :public false
                                              :application-id application-id
                                              :comment "see attachment"
                                              :attachments [{:attachment/id attachment-id}]})))
        (testing ", handler can fetch attachment"
          (is (= (slurp testfile)
                 (-> (api-response :get (str "/api/applications/attachment/" attachment-id) nil
                                   api-key handler-id)
                     assert-response-is-ok
                     :body
                     slurp))))
        (testing ", applicant can't fetch attachment"
          (is (response-is-forbidden? (api-response :get (str "/api/applications/attachment/" attachment-id) nil
                                                    api-key applicant-id))))))

    (testing "handler approves with attachment"
      (let [attachment-id (add-attachment handler-id (file "handler-approve.txt"))]
        (is (number? attachment-id))
        (is (= {:success true} (send-command handler-id
                                             {:type :application.command/approve
                                              :application-id application-id
                                              :comment "see attachment"
                                              :attachments [{:attachment/id attachment-id}]})))))

    (testing "handler closes with two attachments"
      (let [id1 (add-attachment handler-id (file "handler-close1.txt"))
            id2 (add-attachment handler-id (file "handler-close2.txt"))]
        (is (number? id1))
        (is (number? id2))
        (is (= {:success true} (send-command handler-id
                                             {:type :application.command/close
                                              :application-id application-id
                                              :comment "see attachment"
                                              :attachments [{:attachment/id id1}
                                                            {:attachment/id id2}]})))))

    (testing "applicant can see the three new attachments"
      (let [app (get-application application-id applicant-id)
            [close-event approve-event] (reverse (:application/events app))
            [close-id1 close-id2] (map :attachment/id (:event/attachments close-event))
            [approve-id] (map :attachment/id (:event/attachments approve-event))]
        (is (= "application.event/closed" (:event/type close-event)))
        (is (= "application.event/approved" (:event/type approve-event)))
        (is (number? close-id1))
        (is (number? close-id2))
        (is (number? approve-id))
        (assert-response-is-ok (api-response :get (str "/api/applications/attachment/" close-id1) nil
                                             api-key handler-id))
        (assert-response-is-ok (api-response :get (str "/api/applications/attachment/" close-id2) nil
                                             api-key handler-id))
        (assert-response-is-ok (api-response :get (str "/api/applications/attachment/" approve-id) nil
                                             api-key handler-id))))

    (testing ":application/attachments"
      (testing "applicant"
        (is (= ["handler-public-remark.txt"
                "handler-approve.txt"
                "handler-close1.txt"
                "handler-close2.txt"]
               (mapv :attachment/filename (:application/attachments (get-application application-id applicant-id))))))
      (testing "handler"
        (is (= ["handler-public-remark.txt"
                "reviewer-review.txt"
                "handler-private-remark.txt"
                "handler-approve.txt"
                "handler-close1.txt"
                "handler-close2.txt"]
               (mapv :attachment/filename (:application/attachments (get-application application-id handler-id)))))))))

(deftest test-application-api-license-attachments
  (let [api-key "42"
        applicant "alice"
        non-applicant "bob"
        owner "owner"
        handler-user "developer"
        cat-id (test-data/create-catalogue-item! {})
        app-id (test-data/create-application! {:catalogue-item-ids [cat-id]
                                               :actor applicant})
        file-en (clojure.java.io/file "./test-data/test.txt")
        filecontent-en {:tempfile file-en
                        :content-type "text/plain"
                        :filename "test.txt"
                        :size (.length file-en)}
        en-attachment-id (-> (request :post "/api/licenses/add_attachment")
                             (assoc :params {"file" filecontent-en})
                             (assoc :multipart-params {"file" filecontent-en})
                             (authenticate api-key owner)
                             handler
                             read-ok-body
                             :id)

        file-fi (clojure.java.io/file "./test-data/test-fi.txt")
        filecontent-fi {:tempfile file-fi
                        :content-type "text/plain"
                        :filename "test.txt"
                        :size (.length file-fi)}
        fi-attachment-id (-> (request :post "/api/licenses/add_attachment")
                             (assoc :params {"file" filecontent-fi})
                             (assoc :multipart-params {"file" filecontent-fi})
                             (authenticate api-key owner)
                             handler
                             read-ok-body
                             :id)

        license-id (-> (request :post "/api/licenses/create")
                       (authenticate api-key owner)
                       (json-body {:licensetype "attachment"
                                   ;; TODO different content for different languages
                                   :localizations {:en {:title "en title"
                                                        :textcontent "en text"
                                                        :attachment-id en-attachment-id}
                                                   :fi {:title "fi title"
                                                        :textcontent "fi text"
                                                        :attachment-id fi-attachment-id}}})
                       handler
                       read-ok-body
                       :id)]
    (testing "submit application"
      (is (= {:success true}
             (send-command applicant {:type :application.command/submit
                                      :application-id app-id}))))
    (testing "attach license to application"
      (is (= {:success true}
             (send-command handler-user {:type :application.command/add-licenses
                                         :application-id app-id
                                         :comment ""
                                         :licenses [license-id]}))))
    (testing "access license"
      (testing "as applicant"
        (is (= "hello from file\n"
               (-> (request :get (str "/api/applications/" app-id "/license-attachment/" license-id "/en"))
                   (authenticate api-key applicant)
                   handler
                   assert-response-is-ok
                   :body
                   slurp)))
        (testing "in finnish"
          (is (= "tervehdys tiedostosta\n"
                 (-> (request :get (str "/api/applications/" app-id "/license-attachment/" license-id "/fi"))
                     (authenticate api-key applicant)
                     handler
                     assert-response-is-ok
                     :body
                     slurp)))))
      (testing "as handler"
        (is (= "hello from file\n"
               (-> (request :get (str "/api/applications/" app-id "/license-attachment/" license-id "/en"))
                   (authenticate api-key handler-user)
                   handler
                   assert-response-is-ok
                   :body
                   slurp))))
      (testing "as non-applicant"
        (is (response-is-forbidden?
             (-> (request :get (str "/api/applications/" app-id "/license-attachment/" license-id "/en"))
                 (authenticate api-key non-applicant)
                 handler)))))))

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
  (let [applicant "alice"
        handler "developer"
        reviewer "reviewer"
        decider "decider"
        app-id (test-data/create-application! {:actor applicant})]
    (test-data/create-user! {:eppn reviewer})
    (test-data/create-user! {:eppn decider})

    (testing "does not list drafts"
      (is (not (contains? (get-ids (get-todos handler))
                          app-id))))

    (testing "lists submitted in todos"
      (is (= {:success true} (send-command applicant {:type :application.command/submit
                                                      :application-id app-id})))
      (is (contains? (get-ids (get-todos handler))
                     app-id))
      (is (not (contains? (get-ids (get-handled-todos handler))
                          app-id))))

    (testing "search todos"
      (is (contains? (get-ids (get-todos handler {:query (str "applicant:" applicant)}))
                     app-id))
      (is (empty? (get-ids (get-todos handler {:query "applicant:no-such-user"})))))

    (testing "reviewer sees application in todos"
      (is (not (contains? (get-ids (get-todos reviewer))
                          app-id)))
      (is (= {:success true} (send-command handler {:type :application.command/request-review
                                                    :application-id app-id
                                                    :reviewers [reviewer]
                                                    :comment "x"})))
      (is (contains? (get-ids (get-todos reviewer))
                     app-id))
      (is (not (contains? (get-ids (get-handled-todos reviewer))
                          app-id))))

    (testing "decider sees application in todos"
      (is (not (contains? (get-ids (get-todos decider))
                          app-id)))
      (is (= {:success true} (send-command handler {:type :application.command/request-decision
                                                    :application-id app-id
                                                    :deciders [decider]
                                                    :comment "x"})))
      (is (contains? (get-ids (get-todos decider))
                     app-id))
      (is (not (contains? (get-ids (get-handled-todos decider))
                          app-id))))

    (testing "lists handled in handled"
      (is (= {:success true} (send-command handler {:type :application.command/approve
                                                    :application-id app-id
                                                    :comment ""})))
      (is (not (contains? (get-ids (get-todos handler))
                          app-id)))
      (is (contains? (get-ids (get-handled-todos handler))
                     app-id)))

    (testing "search handled todos"
      (is (contains? (get-ids (get-handled-todos handler {:query (str "applicant:" applicant)}))
                     app-id))
      (is (empty? (get-ids (get-handled-todos handler {:query "applicant:no-such-user"})))))

    (testing "reviewer sees accepted application in handled todos"
      (is (not (contains? (get-ids (get-todos reviewer))
                          app-id)))
      (is (contains? (get-ids (get-handled-todos reviewer))
                     app-id)))

    (testing "decider sees accepted application in handled todos"
      (is (not (contains? (get-ids (get-todos decider))
                          app-id)))
      (is (contains? (get-ids (get-handled-todos decider))
                     app-id)))))

(deftest test-application-raw
  (let [api-key "42"
        applicant "alice"
        handler "developer"
        reporter "reporter"
        app-id (test-data/create-application! {:actor applicant})]
    (testing "applicant can't get raw application"
      (is (response-is-forbidden? (api-response :get (str "/api/applications/" app-id "/raw") nil
                                                api-key applicant))))
    (testing "reporter can get raw application"
      (is (= {:application/id app-id
              :application/user-roles {(keyword applicant) ["applicant"]
                                       (keyword handler) ["handler"]
                                       (keyword reporter) ["reporter"]}}
             (-> (api-call :get (str "/api/applications/" app-id "/raw") nil
                           api-key reporter)
                 (select-keys [:application/id :application/user-roles])))))))
