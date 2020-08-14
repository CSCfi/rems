(ns ^:integration rems.api.test-end-to-end
  "Go from zero to an approved application via the API. Check that all side-effects happen."
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.application.approver-bot :as approver-bot]
            [rems.application.rejecter-bot :as rejecter-bot]
            [rems.db.entitlements :as entitlements]
            [rems.email.core :as email]
            [rems.event-notification :as event-notification]
            [rems.json :as json]
            [stub-http.core :as stub]))

(use-fixtures :each api-fixture)

(defn extract-id [resp]
  (assert-success resp)
  (let [id (:id resp)]
    (assert (number? id) (pr-str resp))
    id))

(deftest test-end-to-end
  (testing "clear poller backlog"
    (email/try-send-emails!)
    (entitlements/process-outbox!)
    (event-notification/process-outbox!))
  (with-open [entitlements-server (stub/start! {"/add" {:status 200}
                                                "/remove" {:status 200}})
              event-server (stub/start! {"/event" {:status 200}})]
    ;; TODO should test emails with a mock smtp server
    (let [email-atom (atom [])]
      (with-redefs [rems.config/env (assoc rems.config/env
                                           :smtp-host "localhost"
                                           :smtp-port 25
                                           :languages [:en]
                                           :mail-from "rems@rems.rems"
                                           :entitlements-target {:add (str (:uri entitlements-server) "/add")
                                                                 :remove (str (:uri entitlements-server) "/remove")}
                                           :event-notification-targets [{:url (str (:uri event-server) "/event")
                                                                         :event-types [:application.event/created
                                                                                       :application.event/submitted
                                                                                       :application.event/approved]}])
                    postal.core/send-message (fn [_host message] (swap! email-atom conj message))]
        (let [api-key "42"
              owner-id "owner"
              handler-id "e2e-handler"
              handler-attributes {:userid handler-id
                                  :name "E2E Handler"
                                  :email "handler@example.com"}
              applicant-id "e2e-applicant"
              applicant-attributes {:userid applicant-id
                                    :name "E2E Applicant"
                                    :email "applicant@example.com"}]

          (testing "create organization"
            (api-call :post "/api/organizations/create"
                      {:organization/id "e2e"
                       :organization/name {:fi "Päästä loppuun -testi" :en "End-to-end"}
                       :organization/owners []
                       :organization/review-emails []}
                      api-key owner-id))
          (testing "create users"
            (api-call :post "/api/users/create" handler-attributes api-key owner-id)
            (api-call :post "/api/users/create" applicant-attributes api-key owner-id))

          (let [resource-ext-id "e2e-resource"
                resource-id
                (testing "create resource"
                  (extract-id
                   (api-call :post "/api/resources/create" {:resid resource-ext-id
                                                            :organization {:organization/id "e2e"}
                                                            :licenses []}
                             api-key owner-id)))

                resource-ext-id2 "e2e-resource 2"
                resource-id2
                (testing "create resource 2"
                  (extract-id
                   (api-call :post "/api/resources/create" {:resid resource-ext-id2
                                                            :organization {:organization/id "e2e"}
                                                            :licenses []}
                             api-key owner-id)))

                wf-form-id
                (testing "create workflow form"
                  (extract-id
                   (api-call :post "/api/forms/create" {:form/organization {:organization/id "e2e"}
                                                        :form/title "e2e wf"
                                                        :form/fields [{:field/id "description"
                                                                       :field/type :description
                                                                       :field/title {:en "text field"
                                                                                     :fi "tekstikenttä"
                                                                                     :sv "textfält"}
                                                                       :field/optional false}]}
                             api-key owner-id)))

                form-id
                (testing "create form"
                  (extract-id
                   (api-call :post "/api/forms/create" {:form/organization {:organization/id "e2e"}
                                                        :form/title "e2e"
                                                        :form/fields [{:field/type :text
                                                                       :field/title {:en "text field"
                                                                                     :fi "tekstikenttä"
                                                                                     :sv "tekstfält"}
                                                                       :field/optional false}]}
                             api-key owner-id)))

                form-id2
                (testing "create form 2"
                  (extract-id
                   (api-call :post "/api/forms/create" {:form/organization {:organization/id "e2e"}
                                                        :form/title "e2e 2"
                                                        :form/fields [{:field/id "e2e_fld_2"
                                                                       :field/type :text
                                                                       :field/title {:en "text field 2"
                                                                                     :fi "tekstikenttä 2"
                                                                                     :sv "textfält 2"}
                                                                       :field/optional true}]}
                             api-key owner-id)))
                license-id
                (testing "create license"
                  (extract-id
                   (api-call :post "/api/licenses/create" {:licensetype "link"
                                                           :organization {:organization/id "e2e"}
                                                           :localizations {:en {:title "e2e license" :textcontent "http://example.com"}}}
                             api-key owner-id)))

                workflow-id
                (testing "create workflow"
                  (extract-id
                   (api-call :post "/api/workflows/create" {:organization {:organization/id "e2e"}
                                                            :title "e2e workflow"
                                                            :type :workflow/default
                                                            :forms [{:form/id wf-form-id}]
                                                            :handlers [handler-id]}
                             api-key owner-id)))

                catalogue-item-id
                (testing "create catalogue item"
                  (extract-id
                   (api-call :post "/api/catalogue-items/create" {:resid resource-id
                                                                  :form form-id
                                                                  :wfid workflow-id
                                                                  :organization {:organization/id "e2e"}
                                                                  :localizations {:en {:title "e2e catalogue item"}}}
                             api-key owner-id)))

                catalogue-item-id2
                (testing "create catalogue item 2"
                  (extract-id
                   (api-call :post "/api/catalogue-items/create" {:resid resource-id2
                                                                  :form form-id2
                                                                  :wfid workflow-id
                                                                  :organization {:organization/id "e2e"}
                                                                  :localizations {:en {:title "e2e catalogue item 2"}}}
                             api-key owner-id)))

                application-id
                (testing "create application"
                  (:application-id
                   (assert-success
                    (api-call :post "/api/applications/create" {:catalogue-item-ids [catalogue-item-id catalogue-item-id2]}
                              api-key applicant-id))))]

            (testing "fetch application as applicant"
              (assert (number? application-id))
              (let [application (api-call :get (str "/api/applications/" application-id) nil
                                          api-key applicant-id)]
                (is (= applicant-id (get-in application [:application/applicant :userid])))
                (is (= [resource-ext-id resource-ext-id2] (mapv :resource/ext-id (:application/resources application))))
                (is (= [wf-form-id form-id form-id2] (mapv :form/id (:application/forms application))))))

            (testing "check that application is visible"
              (let [applications (api-call :get "/api/my-applications" nil
                                           api-key applicant-id)]
                (is (= [application-id] (mapv :application/id applications)))))

            (testing "fill in application"
              (assert-success
               (api-call :post "/api/applications/save-draft" {:application-id application-id
                                                               :field-values [{:form wf-form-id
                                                                               :field "description"
                                                                               :value "e2e description"}
                                                                              {:form form-id
                                                                               :field "fld1"
                                                                               :value "e2e test contents"}
                                                                              {:form form-id2
                                                                               :field "e2e_fld_2"
                                                                               :value "e2e test contents 2"}]}
                         api-key applicant-id)))

            (testing "accept terms of use"
              (assert-success
               (api-call :post "/api/applications/accept-licenses" {:application-id application-id
                                                                    :accepted-licenses [license-id]}
                         api-key applicant-id)))

            (testing "submit application"
              (assert-success
               (api-call :post "/api/applications/submit" {:application-id application-id}
                         api-key applicant-id)))

            ;; we could start the pollers normally and wait for them to process the events here for better coverage
            (email/try-send-emails!)

            (testing "email for new application"
              (let [mail (last @email-atom)]
                (is (= "handler@example.com" (:to mail)))
                (is (.contains (:subject mail) "new application"))
                (is (.startsWith (:body mail) "Dear E2E Handler,")))
              (reset! email-atom []))

            (testing "no entitlement yet"
              (let [entitlements (api-call :get "/api/entitlements" nil
                                           api-key owner-id)
                    applications (set (map :application-id entitlements))]
                (is (not (contains? applications application-id)))))

            (testing "fetch application as handler"
              (let [applications (api-call :get "/api/applications/todo" nil
                                           api-key handler-id)
                    todos (set (map (juxt :application/id :application/todo) applications))]
                (is (contains? todos [application-id "new-application"])))
              (let [application (api-call :get (str "/api/applications/" application-id) nil
                                          api-key handler-id)]
                (is (= ["e2e description" "e2e test contents" "e2e test contents 2"]
                       (for [form (:application/forms application)
                             field (:form/fields form)]
                         (:field/value field))))
                (is (= [license-id] (get-in application [:application/accepted-licenses (keyword applicant-id)]))
                    application)))

            (testing "approve application"
              (assert-success
               (api-call :post "/api/applications/approve" {:application-id application-id
                                                            :comment "e2e approved"}
                         api-key handler-id)))

            (email/try-send-emails!)
            (entitlements/process-outbox!)

            (testing "email for approved application"
              (let [mail (last @email-atom)]
                (is (= "applicant@example.com" (:to mail)))
                (is (.contains (:subject mail) "approved"))
                (is (.startsWith (:body mail) "Dear E2E Applicant,")))
              (reset! email-atom []))

            (testing "entitlement"
              (testing "visible via API"
                (let [[entitlement entitlement2 & others] (api-call :get (str "/api/entitlements?user=" applicant-id) nil
                                                                    api-key owner-id)]
                  (is (empty? others))
                  (is (= resource-ext-id (:resource entitlement)))
                  (is (= resource-ext-id2 (:resource entitlement2)))
                  (is (not (:end entitlement)))
                  (is (not (:end entitlement2)))))
              (testing "POSTed to callback"
                (let [[req req2 & others] (stub/recorded-requests entitlements-server)]
                  (is (empty? others))
                  (is (= "/add" (:path req)))
                  (is (= "/add" (:path req2)))
                  (is (= #{{:application application-id
                            :mail "applicant@example.com"
                            :resource resource-ext-id
                            :user applicant-id}
                           {:application application-id
                            :mail "applicant@example.com"
                            :resource resource-ext-id2
                            :user applicant-id}}
                         (set (concat (json/parse-string (get-in req [:body "postData"]))
                                      (json/parse-string (get-in req2 [:body "postData"])))))))))

            (testing "close application"
              (assert-success
               (api-call :post "/api/applications/close" {:application-id application-id
                                                          :comment "e2e closed"}
                         api-key handler-id)))

            (email/try-send-emails!)
            (entitlements/process-outbox!)

            (testing "ended entitlement"
              (testing "visible via API"
                (let [[entitlement entitlement2  & others] (api-call :get (str "/api/entitlements?expired=true&user=" applicant-id) nil
                                                                     api-key owner-id)]
                  (is (empty? others))
                  (is (= resource-ext-id (:resource entitlement)))
                  (is (:end entitlement) entitlement)
                  (is (= resource-ext-id2 (:resource entitlement2)))
                  (is (:end entitlement2) entitlement2)))
              (testing "POSTed to callback"
                (let [[_old _old2 req req2 & others] (stub/recorded-requests entitlements-server)]
                  (is (empty? others))
                  (is (= "/remove" (:path req)))
                  (is (= "/remove" (:path req2)))
                  (is (= #{{:application application-id
                            :mail "applicant@example.com"
                            :resource resource-ext-id
                            :user applicant-id}
                           {:application application-id
                            :mail "applicant@example.com"
                            :resource resource-ext-id2
                            :user applicant-id}}
                         (set (concat (json/parse-string (get-in req [:body "postData"]))
                                      (json/parse-string (get-in req2 [:body "postData"])))))))))

            (testing "fetch application as applicant"
              (let [application (api-call :get (str "/api/applications/" application-id) nil
                                          api-key applicant-id)]
                (is (= "application.state/closed" (:application/state application)))))

            (event-notification/process-outbox!)

            (testing "event notifications"
              (let [requests (stub/recorded-requests event-server)
                    events (for [r requests]
                             (-> r
                                 :body
                                 (get "content")
                                 json/parse-string
                                 (select-keys [:application/id :event/type :event/actor
                                               :application/resources :application/forms
                                               :event/application])
                                 (update :event/application select-keys [:application/id :application/state])))]
                (is (every? (comp #{"PUT"} :method) requests))
                (is (= [{:application/id application-id
                         :event/type "application.event/created"
                         :event/actor applicant-id
                         :application/resources [{:resource/ext-id resource-ext-id :catalogue-item/id catalogue-item-id}
                                                 {:resource/ext-id resource-ext-id2 :catalogue-item/id catalogue-item-id2}]
                         :application/forms [{:form/id wf-form-id} {:form/id form-id} {:form/id form-id2}]
                         :event/application {:application/id application-id
                                             :application/state "application.state/draft"}}
                        {:application/id application-id
                         :event/type "application.event/submitted"
                         :event/actor applicant-id
                         :event/application {:application/id application-id
                                             :application/state "application.state/submitted"}}
                        {:application/id application-id
                         :event/type "application.event/approved"
                         :event/actor handler-id
                         :event/application {:application/id application-id
                                             :application/state "application.state/approved"}}]
                       events))))))))))

(deftest test-approver-rejecter-bots
  (let [api-key "42"
        owner-id "owner"
        handler-id "e2e-handler"
        handler-attributes {:userid handler-id
                            :name "E2E Handler"
                            :email "handler@example.com"}
        applicant-id "e2e-applicant"
        applicant-attributes {:userid applicant-id
                              :name "E2E Applicant"
                              :email "applicant@example.com"}
        approver-attributes {:userid approver-bot/bot-userid
                             :email nil
                             :name "approver"}
        rejecter-attributes {:userid rejecter-bot/bot-userid
                             :email nil
                             :name "rejecter"}]
    (testing "create users"
      (api-call :post "/api/users/create" handler-attributes api-key owner-id)
      (api-call :post "/api/users/create" applicant-attributes api-key owner-id)
      (api-call :post "/api/users/create" approver-attributes api-key owner-id)
      (api-call :post "/api/users/create" rejecter-attributes api-key owner-id))

    (let [resource-ext-id "e2e-resource"
          resource-id
          (testing "create resource"
            (extract-id
             (api-call :post "/api/resources/create" {:resid resource-ext-id
                                                      :organization {:organization/id "e2e"}
                                                      :licenses []}
                       api-key owner-id)))
          form-id
          (testing "create form"
            (extract-id
             (api-call :post "/api/forms/create" {:form/organization {:organization/id "e2e"}
                                                  :form/title "e2e"
                                                  :form/fields [{:field/type :text
                                                                 :field/title {:en "text field"
                                                                               :fi "tekstikenttä"
                                                                               :sv "textfält"}
                                                                 :field/optional true}]}
                       api-key owner-id)))
          workflow-id
          (testing "create workflow"
            (extract-id
             (api-call :post "/api/workflows/create" {:organization {:organization/id "e2e"}
                                                      :title "e2e workflow"
                                                      :type :workflow/default
                                                      :handlers [handler-id
                                                                 approver-bot/bot-userid
                                                                 rejecter-bot/bot-userid]}
                       api-key owner-id)))
          catalogue-item-id
          (testing "create catalogue item"
            (extract-id
             (api-call :post "/api/catalogue-items/create" {:organization {:organization/id "e2e"}
                                                            :resid resource-id
                                                            :form form-id
                                                            :wfid workflow-id
                                                            :localizations {:en {:title "e2e catalogue item"}}}
                       api-key owner-id)))]
      (testing "autoapproved application:"
        (let [application-id (testing "create application"
                               (:application-id
                                (assert-success
                                 (api-call :post "/api/applications/create" {:catalogue-item-ids [catalogue-item-id]}
                                           api-key applicant-id))))]
          (testing "submit application"
            (assert-success
             (api-call :post "/api/applications/submit" {:application-id application-id}
                       api-key applicant-id)))
          (testing "application approved"
            (let [application (api-call :get (str "/api/applications/" application-id) nil
                                        api-key applicant-id)]
              (is (= "application.state/approved" (:application/state application)))))
          (testing "entitlement visible via API"
            (let [[entitlement & others] (api-call :get (str "/api/entitlements?user=" applicant-id) nil
                                                   api-key owner-id)]
              (is (empty? others))
              (is (= resource-ext-id (:resource entitlement)))
              (is (not (:end entitlement)))))
          (testing "revoke"
            (assert-success
             (api-call :post "/api/applications/revoke" {:application-id application-id
                                                         :comment "revoke"}
                       api-key handler-id)))
          (testing "entitlement ended"
            (let [[entitlement & others] (api-call :get (str "/api/entitlements?expired=true&user=" applicant-id) nil
                                                   api-key owner-id)]
              (is (empty? others))
              (is (= resource-ext-id (:resource entitlement)))
              (is (:end entitlement))))
          (testing "user blacklisted"
            (let [[entry & _] (api-call :get (str "/api/blacklist?user=" applicant-id "&resource=" resource-ext-id) nil
                                        api-key handler-id)]
              (is (= {:resource/ext-id resource-ext-id} (:blacklist/resource entry)))
              (is (= applicant-id (:userid (:blacklist/user entry))))))))
      (testing "second application"
        (let [application-id (testing "create application"
                               (:application-id
                                (assert-success
                                 (api-call :post "/api/applications/create" {:catalogue-item-ids [catalogue-item-id]}
                                           api-key applicant-id))))]
          (testing "submit application"
            (assert-success
             (api-call :post "/api/applications/submit" {:application-id application-id}
                       api-key applicant-id)))
          (testing "application rejected"
            (let [application (api-call :get (str "/api/applications/" application-id) nil
                                        api-key applicant-id)]
              (is (= "application.state/rejected" (:application/state application)))))
          (testing "blacklist visible to handler in application"
            (let [application (api-call :get (str "/api/applications/" application-id) nil
                                        api-key handler-id)]
              (is (= [{:blacklist/user applicant-attributes
                       :blacklist/resource {:resource/ext-id resource-ext-id}}]
                     (:application/blacklist application))))))))))
