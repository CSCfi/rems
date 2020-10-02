(ns ^:integration rems.api.test-applications
  (:require [clj-time.core :as time]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.testing :refer :all]
            [rems.db.applications]
            [rems.db.blacklist :as blacklist]
            [rems.db.core :as db]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.handler :refer [handler]]
            [rems.json]
            [rems.testing-util :refer [with-user]]
            [ring.mock.request :refer :all])
  (:import java.io.ByteArrayOutputStream
           java.util.zip.ZipInputStream))

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

(defn- send-command-transit [actor cmd]
  (-> (request :post (str "/api/applications/" (name (:type cmd))))
      (authenticate "42" actor)
      (transit-body (dissoc cmd :type))
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

(defn- get-application-for-user [app-id user-id]
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
        cat-id (test-helpers/create-catalogue-item! {})]
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
  (let [cat-id (test-helpers/create-catalogue-item! {:title {:fi "Fi title" :en "En title"}})
        application-id (test-helpers/create-application! {:actor "alice"
                                                          :catalogue-item-ids [cat-id]})]
    (test-helpers/command! {:type :application.command/submit
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
        license-id1 (test-helpers/create-license! {})
        license-id2 (test-helpers/create-license! {})
        license-id3 (test-helpers/create-license! {})
        license-id4 (test-helpers/create-license! {})
        form-id (test-helpers/create-form! {})
        workflow-id (test-helpers/create-workflow! {:type :workflow/master
                                                    :handlers [handler-id]})
        cat-item-id1 (test-helpers/create-catalogue-item! {:resource-id (test-helpers/create-resource!
                                                                         {:license-ids [license-id1 license-id2]})
                                                           :form-id form-id
                                                           :workflow-id workflow-id})
        cat-item-id2 (test-helpers/create-catalogue-item! {:resource-id (test-helpers/create-resource!
                                                                         {:license-ids [license-id1 license-id2]})
                                                           :form-id form-id
                                                           :workflow-id workflow-id})

        cat-item-id3 (test-helpers/create-catalogue-item! {:resource-id (test-helpers/create-resource!
                                                                         {:license-ids [license-id3]})
                                                           :form-id form-id
                                                           :workflow-id workflow-id})
        application-id (test-helpers/create-application! {:catalogue-item-ids [cat-item-id1]
                                                          :actor user-id})]

    (testing "accept licenses"
      (is (= {:success true} (send-command user-id
                                           {:type :application.command/accept-licenses
                                            :application-id application-id
                                            :accepted-licenses [license-id1 license-id2]})))
      (testing "with invalid application id"
        (is (= {:success false
                :errors [{:type "application-not-found"}]}
               (send-command user-id
                             {:type :application.command/accept-licenses
                              :application-id 9999999999
                              :accepted-licenses [license-id1 license-id2]})))))

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
      (let [application (get-application-for-user application-id user-id)]
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
      (let [application (get-application-for-user application-id handler-id)]
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
          (let [application (get-application-for-user application-id handler-id)]
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
      (let [application (get-application-for-user application-id handler-id)]
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
        (let [eventcount (count (get (get-application-for-user application-id handler-id) :events))]
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
            (let [application (get-application-for-user application-id handler-id)
                  request-event (get-in application [:application/events eventcount])
                  review-event (get-in application [:application/events (inc eventcount)])]
              (is (= (:application/request-id request-event)
                     (:application/request-id review-event)))))))

      (testing "adding and then accepting additional licenses"
        (testing "add licenses"
          (let [application (get-application-for-user application-id user-id)]
            (is (= #{license-id1 license-id2} (license-ids-for-application application)))
            (is (= {:success true} (send-command handler-id
                                                 {:type :application.command/add-licenses
                                                  :application-id application-id
                                                  :licenses [license-id4]
                                                  :comment "Please approve these new terms"})))
            (let [application (get-application-for-user application-id user-id)]
              (is (= #{license-id1 license-id2 license-id4} (license-ids-for-application application))))))
        (testing "applicant accepts the additional licenses"
          (is (= {:success true} (send-command user-id
                                               {:type :application.command/accept-licenses
                                                :application-id application-id
                                                :accepted-licenses [license-id4]})))))

      (testing "changing resources as handler"
        (let [application (get-application-for-user application-id user-id)]
          (is (= #{cat-item-id2} (catalogue-item-ids-for-application application)))
          (is (= #{license-id1 license-id2 license-id4} (license-ids-for-application application)))
          (is (= {:success true} (send-command handler-id
                                               {:type :application.command/change-resources
                                                :application-id application-id
                                                :catalogue-item-ids [cat-item-id3]
                                                :comment "Here are the correct resources"})))
          (let [application (get-application-for-user application-id user-id)]
            (is (= #{cat-item-id3} (catalogue-item-ids-for-application application)))
            ;; TODO: The previously added licenses should probably be retained in the licenses after changing resources.
            (is (= #{license-id3} (license-ids-for-application application))))))

      (testing "changing resources back as handler"
        (is (= {:success true} (send-command handler-id
                                             {:type :application.command/change-resources
                                              :application-id application-id
                                              :catalogue-item-ids [cat-item-id2]})))
        (let [application (get-application-for-user application-id user-id)]
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
        (let [handler-data (get-application-for-user application-id handler-id)
              handler-event-types (map :event/type (get handler-data :application/events))
              applicant-data (get-application-for-user application-id user-id)
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

(deftest test-approve-with-end
  (let [api-key "42"
        applicant "alice"
        handler "developer"]
    (testing "json"
      (let [app-id (test-helpers/create-application! {:actor applicant})]
        (test-helpers/command! {:type :application.command/submit
                                :application-id app-id
                                :actor applicant})
        (is (= {:success true} (send-command handler {:type :application.command/approve
                                                      :application-id app-id
                                                      :comment ""
                                                      :entitlement-end "2100-01-01T00:00:00.000Z"})))
        (let [app (get-application-for-user app-id applicant)]
          (is (= "application.state/approved" (:application/state app)))
          (is (= "2100-01-01T00:00:00.000Z" (:entitlement/end app))))))
    (testing "transit"
      (let [app-id (test-helpers/create-application! {:actor applicant})]
        (test-helpers/command! {:type :application.command/submit
                                :application-id app-id
                                :actor applicant})
        (is (= {:success true} (send-command-transit handler {:type :application.command/approve
                                                              :application-id app-id
                                                              :comment ""
                                                              :entitlement-end (time/date-time 2100 01 01)})))
        (let [app (get-application-for-user app-id applicant)]
          (is (= "application.state/approved" (:application/state app)))
          (is (= "2100-01-01T00:00:00.000Z" (:entitlement/end app))))))))

(deftest test-application-create
  (let [api-key "42"
        user-id "alice"
        application-id (-> (request :post "/api/applications/create")
                           (authenticate "42" user-id)
                           (json-body {:catalogue-item-ids [(test-helpers/create-catalogue-item! {})]})
                           handler
                           read-ok-body
                           :application-id)]

    (testing "creating"
      (is (some? application-id))
      (let [created (get-application-for-user application-id user-id)]
        (is (= "application.state/draft" (get created :application/state)))))

    (testing "seeing draft is forbidden"
      (testing "as unrelated user"
        (is (response-is-forbidden? (api-response :get (str "/api/applications/" application-id) nil api-key "bob"))))
      (testing "as reporter"
        (is (response-is-forbidden? (api-response :get (str "/api/applications/" application-id) nil api-key "reporter"))))
      (testing "as handler"
        (is (response-is-forbidden? (api-response :get (str "/api/applications/" application-id) nil api-key "developer")))))

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
      (let [submitted (get-application-for-user application-id user-id)]
        (is (= "application.state/submitted" (get submitted :application/state)))
        (is (= ["application.event/created"
                "application.event/submitted"]
               (map :event/type (get submitted :application/events))))))

    (testing "seeing submitted application as reporter is allowed"
      (is (response-is-ok?
           (-> (request :get (str "/api/applications/" application-id))
               (authenticate api-key "reporter")
               handler))))))

(deftest test-application-delete
  (let [api-key "42"
        applicant "alice"
        handler "developer"]
    (let [app-id (test-helpers/create-application! {:actor applicant})]
      (testing "can't delete draft as other user"
        (is (= {:errors [{:type "forbidden"}] :success false}
               (api-call :post "/api/applications/delete" {:application-id app-id}
                         api-key handler))))
      (testing "can delete draft as applicant"
        (is (contains? (-> (get-application-for-user app-id applicant)
                           :application/permissions
                           set)
                       "application.command/delete"))
        (is (= {:success true}
               (api-call :post "/api/applications/delete" {:application-id app-id}
                         api-key applicant))))
      (testing "deleted application is gone"
        (is (response-is-not-found?
             (api-response :get (str "/api/applications/" app-id) nil
                           api-key applicant)))))
    (let [app-id (test-helpers/create-application! {:actor applicant})]
      (test-helpers/command! {:application-id app-id
                              :type :application.command/submit
                              :actor applicant})
      (testing "can't delete submitted application"
        (is (= {:errors [{:type "forbidden"}] :success false}
               (api-call :post "/api/applications/delete" {:application-id app-id}
                         api-key applicant))))
      (test-helpers/command! {:application-id app-id
                              :type :application.command/return
                              :actor handler})
      (testing "can't delete returned application"
        (is (= {:errors [{:type "forbidden"}] :success false}
               (api-call :post "/api/applications/delete" {:application-id app-id}
                         api-key applicant)))))))

(deftest test-application-close
  (let [user-id "alice"
        application-id (test-helpers/create-application! {:actor user-id})]
    (test-helpers/command! {:application-id application-id
                            :type :application.command/submit
                            :actor user-id})
    (is (= {:success true}
           (send-command "developer" {:type :application.command/close
                                      :application-id application-id
                                      :comment ""})))
    (is (= "application.state/closed"
           (:application/state (get-application-for-user application-id user-id))))))

(deftest test-application-submit
  (let [owner "owner"
        user-id "alice"
        form-id (test-helpers/create-form! {})
        cat-id (test-helpers/create-catalogue-item! {:form-id form-id})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id] :actor user-id})
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
        form-id (test-helpers/create-form! {:form/fields [{:field/id "req1"
                                                           :field/title {:en "req"
                                                                         :fi "pak"
                                                                         :sv "obl"}
                                                           :field/type :text
                                                           :field/optional false}
                                                          {:field/id "opt1"
                                                           :field/title {:en "opt"
                                                                         :fi "val"
                                                                         :sv "fri"}
                                                           :field/type :text
                                                           :field/optional true}]})
        form-id2 (test-helpers/create-form! {:form/fields [{:field/id "req2"
                                                            :field/title {:en "req"
                                                                          :fi "pak"
                                                                          :sv "obl"}
                                                            :field/type :text
                                                            :field/optional false}
                                                           {:field/id "opt2"
                                                            :field/title {:en "opt"
                                                                          :fi "val"
                                                                          :sv "fri"}
                                                            :field/type :text
                                                            :field/optional true}
                                                           {:field/id "optionlist"
                                                            :field/title {:en "Option list."
                                                                          :fi "Valintalista."
                                                                          :sv "Välj"}
                                                            :field/type :option
                                                            :field/options [{:key "Option1"
                                                                             :label {:en "First"
                                                                                     :fi "Ensimmäinen"
                                                                                     :sv "Först"}}
                                                                            {:key "Option2"
                                                                             :label {:en "Second"
                                                                                     :fi "Toinen"
                                                                                     :sv "Den andra"}}
                                                                            {:key "Option3"
                                                                             :label {:en "Third"
                                                                                     :fi "Kolmas"
                                                                                     :sv "Tredje"}}]
                                                            :field/optional true}]})
        wf-id (test-helpers/create-workflow! {})
        cat-id (test-helpers/create-catalogue-item! {:form-id form-id :workflow-id wf-id})
        cat-id2 (test-helpers/create-catalogue-item! {:form-id form-id2 :workflow-id wf-id})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id cat-id2]
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

    (testing "save-draft fails with non-existing value of option list"
      (is (= {:success false
              :errors [{:field-id "optionlist", :form-id form-id2, :type "t.form.validation/invalid-value"}]}
             (send-command user-id {:type           :application.command/save-draft
                                    :application-id app-id
                                    :field-values   [{:form form-id :field "opt1" :value "opt"}
                                                     {:form form-id :field "req1" :value "req"}
                                                     {:form form-id2 :field "opt2" :value "opt"}
                                                     {:form form-id2 :field "req2" :value "req"}
                                                     {:form form-id2 :field "optionlist" :value "foobar"}]}))))

    (testing "set existing value of option list"
      (is (= {:success true}
             (send-command user-id {:type :application.command/save-draft
                                    :application-id app-id
                                    :field-values [{:form form-id :field "opt1" :value "opt"}
                                                   {:form form-id :field "req1" :value "req"}
                                                   {:form form-id2 :field "opt2" :value "opt"}
                                                   {:form form-id2 :field "req2" :value "req"}
                                                   {:form form-id2 :field "optionlist" :value "Option2"}]}))))

    (testing "can submit with required field"
      (is (= {:success true}
             (send-command user-id {:type :application.command/submit
                                    :application-id app-id}))))))

(deftest test-decider-workflow
  (let [applicant "alice"
        handler "handler"
        decider "carl"
        wf-id (test-helpers/create-workflow! {:type :workflow/decider
                                              :handlers [handler]})
        cat-id (test-helpers/create-catalogue-item! {:workflow-id wf-id})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                  :actor applicant})]
    (testing "applicant's commands for draft"
      (is (= #{"application.command/accept-licenses"
               "application.command/change-resources"
               "application.command/copy-as-new"
               "application.command/delete"
               "application.command/invite-member"
               "application.command/remove-member"
               "application.command/save-draft"
               "application.command/submit"
               "application.command/uninvite-member"}
             (set (:application/permissions (get-application-for-user app-id applicant))))))
    (testing "submit"
      (is (= {:success true}
             (send-command applicant {:type :application.command/submit
                                      :application-id app-id}))))
    (testing "applicant's commands after submit"
      (is (= #{"application.command/accept-licenses"
               "application.command/copy-as-new"
               "application.command/remove-member"
               "application.command/uninvite-member"}
             (set (:application/permissions (get-application-for-user app-id applicant))))))
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
             (set (:application/permissions (get-application-for-user app-id handler))))))
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
             (set (:application/permissions (get-application-for-user app-id decider))))))
    (testing "approve"
      (is (= {:success true}
             (send-command decider {:type :application.command/approve
                                    :application-id app-id
                                    :comment ""}))))))

(deftest test-revoke
  (let [applicant-id "alice"
        member-id "malice"
        handler-id "handler"
        wfid (test-helpers/create-workflow! {:handlers [handler-id]})
        formid (test-helpers/create-form! {})
        ext1 "revoke-test-resource-1"
        ext2 "revoke-test-resource-2"
        res1 (test-helpers/create-resource! {:resource-ext-id ext1})
        res2 (test-helpers/create-resource! {:resource-ext-id ext2})
        cat1 (test-helpers/create-catalogue-item! {:workflow-id wfid :form-id formid :resource-id res1})
        cat2 (test-helpers/create-catalogue-item! {:workflow-id wfid :form-id formid :resource-id res2})
        app-id (test-helpers/create-application! {:actor applicant-id :catalogue-item-ids [cat1 cat2]})]
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

(deftest test-hiding-sensitive-information
  (let [applicant-id "alice"
        member-id "developer"
        handler-id "handler"
        api-key "42"
        wfid (test-helpers/create-workflow! {:handlers [handler-id]})
        cat1 (test-helpers/create-catalogue-item! {:workflow-id wfid})
        ;; TODO blacklist?
        app-id (test-helpers/create-application! {:actor applicant-id :catalogue-item-ids [cat1]})]
    (testing "set up approved application with multiple members"
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
    (testing "handler can see extra user attributes"
      (let [application (api-call :get (str "/api/applications/" app-id) nil
                                  api-key handler-id)]
        (is (= {:userid "alice"
                :name "Alice Applicant"
                :email "alice@example.com"
                :organizations [{:organization/id "default"}]
                :nickname "In Wonderland"}
               (:application/applicant application)
               (get-in application [:application/events 0 :event/actor-attributes])))
        (is (= {:userid "developer"
                :name "Developer"
                :email "developer@example.com"
                :nickname "The Dev"}
               (first (:application/members application))
               (get-in application [:application/events 2 :application/member])))))
    (doseq [user [applicant-id member-id]]
      (testing (str user " can't see extra user attributes")
        (let [application (api-call :get (str "/api/applications/" app-id) nil
                                    api-key user)]
          (is (= {:userid "alice"
                  :name "Alice Applicant"
                  :email "alice@example.com"
                  :organizations [{:organization/id "default"}]}
                 (:application/applicant application)
                 (get-in application [:application/events 0 :event/actor-attributes])))
          (is (= {:userid "developer"
                  :name "Developer"
                  :email "developer@example.com"}
                 (first (:application/members application))
                 (get-in application [:application/events 2 :application/member]))))))))

(deftest test-application-export
  (let [applicant "alice"
        handler "handler"
        reporter "reporter"
        api-key "42"
        wf-id (test-helpers/create-workflow! {:type :workflow/default
                                              :handlers [handler]})
        form-id (test-helpers/create-form! {:form/fields [{:field/id "fld1"
                                                           :field/type :text
                                                           :field/title {:en "Field 1" :fi "Field 1" :sv "Field 1"}
                                                           :field/optional false}]})
        form-2-id (test-helpers/create-form! {:form/fields [{:field/id "fld2"
                                                             :field/type :text
                                                             :field/title {:en "HIDDEN" :fi "HIDDEN" :sv "HIDDEN"}
                                                             :field/optional false}]})
        cat-id (test-helpers/create-catalogue-item! {:title {:en "Item1"}
                                                     :workflow-id wf-id
                                                     :form-id form-id})
        cat-2-id (test-helpers/create-catalogue-item! {:title {:en "Item2"}
                                                       :workflow-id wf-id
                                                       :form-id form-2-id})
        app-id (test-helpers/create-draft! applicant [cat-id] "Answer1")
        _draft-app-id (test-helpers/create-draft! applicant [cat-id] "DraftAnswer")
        app-2-id (test-helpers/create-draft! applicant [cat-id cat-2-id] "Answer2")]
    (send-command applicant {:type :application.command/submit
                             :application-id app-id})
    (send-command applicant {:type :application.command/submit
                             :application-id app-2-id})
    (testing "reporter can export"
      (let [exported (api-call :get (str "/api/applications/export?form-id=" form-id) nil
                               api-key reporter)
            [_header & lines] (str/split-lines exported)]
        (is (str/includes? exported "Field 1")
            exported)
        (is (not (str/includes? exported "HIDDEN"))
            exported)
        (testing "drafts are not visible"
          (is (not (str/includes? exported "DraftAnswer"))))
        (testing "submitted applications are visible"
          (is (= 2 (count lines)))
          (is (some #(str/includes? % "\"Item1\",\"Answer1\"") lines)
              lines)
          (is (some #(str/includes? % "\"Item1, Item2\",\"Answer2\"") lines)
              lines))))
    (testing "handler can't export"
      (is (response-is-forbidden? (api-response :get (str "/api/applications/export?form-id=" form-id) nil
                                                api-key handler))))))

(def testfile (io/file "./test-data/test.txt"))

(def malicious-file (io/file "./test-data/malicious_test.html"))

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
        handler-id "developer" ;; developer is the default handler in test-helpers
        form-id (test-helpers/create-form! {:form/fields [{:field/id "attach"
                                                           :field/title {:en "some attachment"
                                                                         :fi "joku liite"
                                                                         :sv "bilaga"}
                                                           :field/type :attachment
                                                           :field/optional true}]})
        cat-id (test-helpers/create-catalogue-item! {:form-id form-id})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
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
    (testing "invalid value for attachment field"
      (is (= {:success false
              :errors [{:form-id form-id
                        :field-id "attach"
                        :type "t.form.validation/invalid-value"}]}
             (send-command user-id {:type :application.command/save-draft
                                    :application-id app-id
                                    :field-values [{:form form-id :field "attach" :value "1,a"}]}))))
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
        (testing "and uploading an attachment with the same name"
          (let [id (-> (upload-request filecontent)
                       (authenticate api-key user-id)
                       handler
                       read-ok-body
                       :id)]
            (is (number? id))
            (testing "and retrieving it"
              (let [response (-> (read-request id)
                                 (authenticate api-key user-id)
                                 handler
                                 assert-response-is-ok)]
                (is (= "attachment;filename=\"test (1).txt\"" (get-in response [:headers "Content-Disposition"])))
                (is (= (slurp testfile) (slurp (:body response))))))))
        (testing "and retrieving it as non-applicant"
          (let [response (-> (read-request id)
                             (authenticate api-key "carl")
                             handler)]
            (is (response-is-forbidden? response))))
        (testing "and uploading a second attachment"
          (let [body2 (-> (upload-request (assoc filecontent :filename "second.txt"))
                          (authenticate api-key user-id)
                          handler
                          read-ok-body)
                id2 (:id body2)]
            (is (:success body2))
            (is (number? id2))
            (testing "and using them in a field"
              (is (= {:success true}
                     (send-command user-id {:type :application.command/save-draft
                                            :application-id app-id
                                            :field-values [{:form form-id :field "attach" :value (str id "," id2)}]}))))
            (testing "and submitting"
              (is (= {:success true}
                     (send-command user-id {:type :application.command/submit
                                            :application-id app-id}))))
            (testing "and accessing the attachments as handler"
              (let [response (-> (read-request id)
                                 (authenticate api-key handler-id)
                                 handler
                                 assert-response-is-ok)]
                (is (= "attachment;filename=\"test.txt\"" (get-in response [:headers "Content-Disposition"])))
                (is (= (slurp testfile) (slurp (:body response)))))
              (let [response (-> (read-request id2)
                                 (authenticate api-key handler-id)
                                 handler
                                 assert-response-is-ok)]
                (is (= "attachment;filename=\"second.txt\"" (get-in response [:headers "Content-Disposition"])))
                (is (= (slurp testfile) (slurp (:body response))))))
            (testing "and copying the application"
              (let [response (send-command user-id {:type :application.command/copy-as-new
                                                    :application-id app-id})
                    new-app-id (:application-id response)]
                (is (:success response))
                (is (number? new-app-id))
                (testing "and fetching the copied attachent"
                  (let [new-app (get-application-for-user new-app-id user-id)
                        [new-id new-id2] (mapv :attachment/id (get new-app :application/attachments))]
                    (is (number? new-id))
                    (is (number? new-id2))
                    (is (not= #{id id2} #{new-id new-id2}))
                    (let [response (-> (read-request new-id)
                                       (authenticate api-key user-id)
                                       handler
                                       assert-response-is-ok)]
                      (is (= "attachment;filename=\"test.txt\"" (get-in response [:headers "Content-Disposition"])))
                      (is (= (slurp testfile) (slurp (:body response)))))
                    (let [response (-> (read-request new-id2)
                                       (authenticate api-key user-id)
                                       handler
                                       assert-response-is-ok)]
                      (is (= "attachment;filename=\"second.txt\"" (get-in response [:headers "Content-Disposition"])))
                      (is (= (slurp testfile) (slurp (:body response)))))))))))))
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
        workflow-id (test-helpers/create-workflow! {:type :workflow/master
                                                    :handlers [handler-id]})
        cat-item-id (test-helpers/create-catalogue-item! {:workflow-id workflow-id})
        application-id (test-helpers/create-application! {:catalogue-item-ids [cat-item-id]
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
      (let [app (get-application-for-user application-id applicant-id)
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

    (testing "handler closes with two attachments (with the same name)"
      (let [id1 (add-attachment handler-id (file "handler-close.txt"))
            id2 (add-attachment handler-id (file "handler-close.txt"))]
        (is (number? id1))
        (is (number? id2))
        (is (= {:success true} (send-command handler-id
                                             {:type :application.command/close
                                              :application-id application-id
                                              :comment "see attachment"
                                              :attachments [{:attachment/id id1}
                                                            {:attachment/id id2}]})))))

    (testing "applicant can see the three new attachments"
      (let [app (get-application-for-user application-id applicant-id)
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
                "handler-close.txt"
                "handler-close (1).txt"]
               (mapv :attachment/filename (:application/attachments (get-application-for-user application-id applicant-id))))))
      (testing "handler"
        (is (= ["handler-public-remark.txt"
                "reviewer-review.txt"
                "handler-private-remark.txt"
                "handler-approve.txt"
                "handler-close.txt"
                "handler-close (1).txt"]
               (mapv :attachment/filename (:application/attachments (get-application-for-user application-id handler-id)))))))))

(deftest test-application-attachment-zip
  (let [api-key "42"
        applicant-id "alice"
        handler-id "handler"
        reporter-id "reporter"
        workflow-id (test-helpers/create-workflow! {:handlers [handler-id]})
        form-id (test-helpers/create-form! {:form/fields [{:field/id "attach1"
                                                           :field/title {:en "some attachment"
                                                                         :fi "joku liite"
                                                                         :sv "bilaga"}
                                                           :field/type :attachment
                                                           :field/optional true}
                                                          {:field/id "attach2"
                                                           :field/title {:en "another attachment"
                                                                         :fi "toinen liite"
                                                                         :sv "annan bilaga"}
                                                           :field/type :attachment
                                                           :field/optional true}]})
        cat-id (test-helpers/create-catalogue-item! {:workflow-id workflow-id
                                                     :form-id form-id})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                  :actor applicant-id})
        add-attachment (fn [user file]
                         (-> (request :post (str "/api/applications/add-attachment?application-id=" app-id))
                             (authenticate api-key user)
                             (assoc :params {"file" file})
                             (assoc :multipart-params {"file" file})
                             handler
                             read-ok-body
                             :id))
        file #(assoc filecontent :filename %)
        fetch-zip (fn [user-id]
                    (with-open [zip (-> (api-response :get (str "/api/applications/" app-id "/attachments") nil
                                                      api-key user-id)
                                        :body
                                        ZipInputStream.)]
                      (loop [files {}]
                        (if-let [entry (.getNextEntry zip)]
                          (let [buf (ByteArrayOutputStream.)]
                            (io/copy zip buf)
                            (recur (assoc files (.getName entry) (.toString buf "UTF-8"))))
                          files))))]
    (testing "save a draft"
      (let [id (add-attachment applicant-id (file "invisible.txt"))]
        (is (= {:success true}
               (send-command applicant-id {:type :application.command/save-draft
                                           :application-id app-id
                                           :field-values [{:form form-id :field "attach1" :value (str id)}]})))))
    (testing "save a new draft"
      (let [blue-id (add-attachment applicant-id (file "blue.txt"))
            green-id (add-attachment applicant-id (file "green.txt"))
            red-id (add-attachment applicant-id (file "red.txt"))]
        (is (= {:success true}
               (send-command applicant-id {:type :application.command/save-draft
                                           :application-id app-id
                                           :field-values [{:form form-id :field "attach1" :value (str blue-id "," green-id)}
                                                          {:form form-id :field "attach2" :value (str red-id)}]})))))
    (testing "fetch zip as applicant"
      (is (= {"blue.txt" (slurp testfile)
              "red.txt" (slurp testfile)
              "green.txt" (slurp testfile)}
             (fetch-zip applicant-id))))
    (testing "submit"
      (is (= {:success true}
             (send-command applicant-id {:type :application.command/submit
                                         :application-id app-id}))))
    (testing "remark with attachments"
      (let [blue-comment-id (add-attachment handler-id (file "blue.txt"))
            yellow-comment-id (add-attachment handler-id (file "yellow.txt"))]
        (is (= {:success true} (send-command handler-id
                                             {:type :application.command/remark
                                              :public true
                                              :application-id app-id
                                              :comment "see attachment"
                                              :attachments [{:attachment/id blue-comment-id}
                                                            {:attachment/id yellow-comment-id}]}))))
      (testing "fetch zip as applicant, handler and reporter"
        (is (= {"blue.txt" (slurp testfile)
                "red.txt" (slurp testfile)
                "green.txt" (slurp testfile)
                "blue (1).txt" (slurp testfile)
                "yellow.txt" (slurp testfile)}
               (fetch-zip applicant-id)
               (fetch-zip handler-id)
               (fetch-zip reporter-id))))
      (testing "fetch zip as third party"
        (is (response-is-forbidden? (api-response :get (str "/api/applications/" app-id "/attachments") nil
                                                  api-key "malice"))))
      (testing "fetch zip for nonexisting application"
        (is (response-is-not-found? (api-response :get "/api/applications/99999999/attachments" nil
                                                  api-key "malice")))))))


(deftest test-application-api-license-attachments
  (let [api-key "42"
        applicant "alice"
        non-applicant "bob"
        owner "owner"
        handler-user "developer"
        cat-id (test-helpers/create-catalogue-item! {})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
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
                                   :organization {:organization/id "abc"}
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
        cat-id (test-helpers/create-catalogue-item! {})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
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
        (is (= {:error "not found"} (read-body response)))))

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
  (let [app-id (test-helpers/create-application! {:actor "alice"})]

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
        app-id (test-helpers/create-application! {:actor applicant})]
    (test-helpers/create-user! {:eppn reviewer})
    (test-helpers/create-user! {:eppn decider})

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
        handler "handler"
        reporter "reporter"
        form-id (test-helpers/create-form! {:form/title "notifications"
                                            :form/fields [{:field/type :text
                                                           :field/id "field-1"
                                                           :field/title {:en "text field"
                                                                         :fi "text field"
                                                                         :sv "text field"}
                                                           :field/optional false}]})
        workflow-id (test-helpers/create-workflow! {:title "wf"
                                                    :handlers [handler]
                                                    :type :workflow/default})
        ext-id "resres"
        res-id (test-helpers/create-resource! {:resource-ext-id ext-id})
        cat-id (test-helpers/create-catalogue-item! {:form-id form-id
                                                     :resource-id res-id
                                                     :workflow-id workflow-id})
        app-id (test-helpers/create-draft! applicant [cat-id] "raw test" (time/date-time 2010))]
    (testing "applicant can't get raw application"
      (is (response-is-forbidden? (api-response :get (str "/api/applications/" app-id "/raw") nil
                                                api-key applicant))))
    (testing "reporter can get raw application"
      (is (= {:application/description ""
              :application/invited-members []
              :application/last-activity "2010-01-01T00:00:00.000Z"
              :application/attachments []
              :application/licenses []
              :application/created "2010-01-01T00:00:00.000Z"
              :application/state "application.state/draft"
              :application/role-permissions
              {:everyone-else ["application.command/accept-invitation"]
               :member ["application.command/copy-as-new"
                        "application.command/accept-licenses"]
               :reporter ["see-everything"]
               :applicant ["application.command/copy-as-new"
                           "application.command/invite-member"
                           "application.command/submit"
                           "application.command/remove-member"
                           "application.command/accept-licenses"
                           "application.command/uninvite-member"
                           "application.command/delete"
                           "application.command/save-draft"
                           "application.command/change-resources"]}
              :application/modified "2010-01-01T00:00:00.000Z"
              :application/user-roles {:alice ["applicant"] :handler ["handler"] :reporter ["reporter"]}
              :application/external-id "2010/1"
              :application/workflow {:workflow/type "workflow/default"
                                     :workflow/id workflow-id
                                     :workflow.dynamic/handlers
                                     [{:email "handler@example.com" :userid "handler" :name "Hannah Handler"}]}
              :application/blacklist []
              :application/id app-id
              :application/todo nil
              :application/applicant {:email "alice@example.com" :userid "alice" :name "Alice Applicant" :nickname "In Wonderland" :organizations [{:organization/id "default"}]}
              :application/members []
              :application/resources [{:catalogue-item/start "REDACTED"
                                       :catalogue-item/end nil
                                       :catalogue-item/expired false
                                       :catalogue-item/enabled true
                                       :resource/id res-id
                                       :catalogue-item/title {}
                                       :catalogue-item/infourl {}
                                       :resource/ext-id ext-id
                                       :catalogue-item/archived false
                                       :catalogue-item/id cat-id}]
              :application/accepted-licenses {:alice []}
              :application/forms [{:form/fields [{:field/value "raw test"
                                                  :field/type "text"
                                                  :field/title {:en "text field"
                                                                :fi "text field"
                                                                :sv "text field"}
                                                  :field/id "field-1"
                                                  :field/optional false
                                                  :field/visible true
                                                  :field/private false}]
                                   :form/title "notifications"
                                   :form/id form-id}]
              :application/events [{:application/external-id "2010/1"
                                    :event/actor-attributes {:userid "alice" :name "Alice Applicant" :nickname "In Wonderland" :email "alice@example.com" :organizations [{:organization/id "default"}]}
                                    :application/id app-id
                                    :event/time "2010-01-01T00:00:00.000Z"
                                    :workflow/type "workflow/default"
                                    :application/resources [{:catalogue-item/id cat-id :resource/ext-id ext-id}]
                                    :application/forms [{:form/id form-id}]
                                    :workflow/id workflow-id
                                    :event/actor "alice"
                                    :event/type "application.event/created"
                                    :event/id 100
                                    :application/licenses []}
                                   {:event/id 100
                                    :event/type "application.event/draft-saved"
                                    :event/time "2010-01-01T00:00:00.000Z"
                                    :event/actor "alice"
                                    :application/id app-id
                                    :event/actor-attributes {:userid "alice" :name "Alice Applicant" :nickname "In Wonderland" :email "alice@example.com" :organizations [{:organization/id "default"}]}
                                    :application/field-values [{:form form-id :field "field-1" :value "raw test"}]}
                                   {:event/id 100
                                    :event/type "application.event/licenses-accepted"
                                    :event/time "2010-01-01T00:00:00.000Z"
                                    :event/actor "alice"
                                    :application/id app-id
                                    :event/actor-attributes {:userid "alice" :name "Alice Applicant" :nickname "In Wonderland" :email "alice@example.com" :organizations [{:organization/id "default"}]}
                                    :application/accepted-licenses []}]}
             (-> (api-call :get (str "/api/applications/" app-id "/raw") nil
                           api-key reporter)
                 ;; start is set by the db not easy to mock
                 (assoc-in [:application/resources 0 :catalogue-item/start] "REDACTED")
                 ;; event ids are unpredictable
                 (update :application/events (partial map #(update % :event/id (constantly 100))))))))))
