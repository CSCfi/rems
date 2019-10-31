(ns ^:integration rems.api.test-end-to-end
  "Go from zero to an approved application via the API. Check that all side-effects happen."
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.application.approver-bot :as approver-bot]
            [rems.db.test-entitlements :as test-entitlements]
            [rems.json :as json]
            [rems.poller.entitlements :as entitlements-poller]
            [rems.poller.email :as email-poller]
            [stub-http.core :as stub]))

(use-fixtures :each api-fixture)

(defn extract-id [resp]
  (assert-success resp)
  (let [id (:id resp)]
    (assert (number? id) (pr-str resp))
    id))

(deftest test-end-to-end
  (testing "clear poller backlog"
    (entitlements-poller/run)
    (email-poller/run))
  (with-open [entitlements-server (stub/start! {"/add" {:status 200}
                                                "/remove" {:status 200}})]
    ;; TODO should test emails with a mock smtp server
    (let [email-atom (atom [])]
      (with-redefs [rems.config/env (assoc rems.config/env
                                           :smtp-host "localhost"
                                           :smtp-port 25
                                           :mail-from "rems@rems.rems"
                                           :entitlements-target {:add (str (:uri entitlements-server) "/add")
                                                                 :remove (str (:uri entitlements-server) "/remove")})
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
          (testing "create users"
            (api-call :post "/api/users/create" handler-attributes api-key owner-id)
            (api-call :post "/api/users/create" applicant-attributes api-key owner-id))

          (let [resource-ext-id "e2e-resource"
                resource-id
                (testing "create resource"
                  (extract-id
                   (api-call :post "/api/resources/create" {:resid resource-ext-id
                                                            :organization "e2e"
                                                            :licenses []}
                             api-key owner-id)))
                form-id
                (testing "create form"
                  (extract-id
                   (api-call :post "/api/forms/create" {:form/organization "e2e"
                                                        :form/title "e2e"
                                                        :form/fields [{:field/type :text
                                                                       :field/title {:en "text field"}
                                                                       :field/optional false}]}
                             api-key owner-id)))
                license-id
                (testing "create license"
                  (extract-id
                   (api-call :post "/api/licenses/create" {:licensetype "link"
                                                           :localizations {:en {:title "e2e license" :textcontent "http://example.com"}}}
                             api-key owner-id)))

                workflow-id
                (testing "create workflow"
                  (extract-id
                   (api-call :post "/api/workflows/create" {:organization "e2e"
                                                            :title "e2e workflow"
                                                            :type :dynamic
                                                            :handlers [handler-id]}
                             api-key owner-id)))

                catalogue-item-id
                (testing "create catalogue item"
                  (extract-id
                   (api-call :post "/api/catalogue-items/create" {:resid resource-id
                                                                  :form form-id
                                                                  :wfid workflow-id
                                                                  :localizations {:en {:title "e2e catalogue item"}}}
                             api-key owner-id)))

                application-id
                (testing "create application"
                  (:application-id
                   (assert-success
                    (api-call :post "/api/applications/create" {:catalogue-item-ids [catalogue-item-id]}
                              api-key applicant-id))))]

            (testing "fetch application as applicant"
              (assert (number? application-id))
              (let [application (api-call :get (str "/api/applications/" application-id) nil
                                          api-key applicant-id)]
                (is (= applicant-id (get-in application [:application/applicant :userid])))
                (is (= [resource-ext-id] (mapv :resource/ext-id (:application/resources application))))))

            (testing "check that application is visible"
              (let [applications (api-call :get "/api/my-applications" nil
                                           api-key applicant-id)]
                (is (= [application-id] (mapv :application/id applications)))))

            (testing "fill in application"
              (assert-success
               (api-call :post "/api/applications/save-draft" {:application-id application-id
                                                               :field-values [{:field 1
                                                                               :value "e2e test contents"}]}
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
            (email-poller/run)
            (entitlements-poller/run)

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
                (is (= "e2e test contents" (get-in application [:application/form :form/fields 0 :field/value])))
                (is (= [license-id] (get-in application [:application/accepted-licenses (keyword applicant-id)]))
                    application)))

            (testing "approve application"
              (assert-success
               (api-call :post "/api/applications/approve" {:application-id application-id
                                                            :comment "e2e approved"}
                         api-key handler-id)))

            (email-poller/run)
            (entitlements-poller/run)

            (testing "email for approved application"
              (let [mail (last @email-atom)]
                (is (= "applicant@example.com" (:to mail)))
                (is (.contains (:subject mail) "approved"))
                (is (.startsWith (:body mail) "Dear E2E Applicant,")))
              (reset! email-atom []))

            (testing "entitlement"
              (testing "visible via API"
                (let [[entitlement & others] (api-call :get (str "/api/entitlements?user=" applicant-id) nil
                                                       api-key owner-id)]
                  (is (empty? others))
                  (is (= resource-ext-id (:resource entitlement)))
                  (is (not (:end entitlement)))))
              (testing "POSTed to callback"
                (let [[req & others] (stub/recorded-requests entitlements-server)]
                  (is (empty? others))
                  (is (= "/add" (:path req)))
                  (is (= [{:application application-id
                           :mail "applicant@example.com"
                           :resource resource-ext-id
                           :user applicant-id}]
                         (json/parse-string (get-in req [:body "postData"])))))))

            (testing "close application"
              (assert-success
               (api-call :post "/api/applications/close" {:application-id application-id
                                                          :comment "e2e closed"}
                         api-key handler-id)))

            (email-poller/run)
            (entitlements-poller/run)

            (testing "ended entitlement"
              (testing "visible via API"
                (let [[entitlement & others] (api-call :get (str "/api/entitlements?expired=true&user=" applicant-id) nil
                                                       api-key owner-id)]
                  (is (empty? others))
                  (is (= resource-ext-id (:resource entitlement)))
                  (is (:end entitlement) entitlement)))
              (testing "POSTed to callback"
                (let [[_old req & others] (stub/recorded-requests entitlements-server)]
                  (is (empty? others))
                  (is (= "/remove" (:path req)))
                  (is (= [{:application application-id
                           :mail "applicant@example.com"
                           :resource resource-ext-id
                           :user applicant-id}]
                         (json/parse-string (get-in req [:body "postData"])))))))

            (testing "fetch application as applicant"
              (let [application (api-call :get (str "/api/applications/" application-id) nil
                                          api-key applicant-id)]
                (is (= "application.state/closed" (:application/state application)))))))))))

(deftest test-approver-bot
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
        bot-attributes {:userid approver-bot/bot-userid
                        :email nil
                        :name "bot"}]
    (testing "create users"
      (api-call :post "/api/users/create" handler-attributes api-key owner-id)
      (api-call :post "/api/users/create" applicant-attributes api-key owner-id)
      (api-call :post "/api/users/create" bot-attributes api-key owner-id))

    (let [resource-ext-id "e2e-resource"
          resource-id
          (testing "create resource"
            (extract-id
             (api-call :post "/api/resources/create" {:resid resource-ext-id
                                                      :organization "e2e"
                                                      :licenses []}
                       api-key owner-id)))
          form-id
          (testing "create form"
            (extract-id
             (api-call :post "/api/forms/create" {:form/organization "e2e"
                                                  :form/title "e2e"
                                                  :form/fields [{:field/type :text
                                                                 :field/title {:en "text field"}
                                                                 :field/optional true}]}
                       api-key owner-id)))
          workflow-id
          (testing "create workflow"
            (extract-id
             (api-call :post "/api/workflows/create" {:organization "e2e"
                                                      :title "e2e workflow"
                                                      :type :dynamic
                                                      :handlers [handler-id approver-bot/bot-userid]}
                       api-key owner-id)))
          catalogue-item-id
          (testing "create catalogue item"
            (extract-id
             (api-call :post "/api/catalogue-items/create" {:resid resource-id
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
            (entitlements-poller/run)
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
            (entitlements-poller/run)
            (let [[entitlement & others] (api-call :get (str "/api/entitlements?expired=true&user=" applicant-id) nil
                                                   api-key owner-id)]
              (is (empty? others))
              (is (= resource-ext-id (:resource entitlement)))
              (is (:end entitlement))))
          (testing "user blacklisted"
            (let [[entry & _] (api-call :get (str "/api/blacklist?user=" applicant-id "&resource=" resource-ext-id) nil
                                        api-key handler-id)]
              (is (= resource-ext-id (:resource entry)))
              (is (= applicant-id (:userid (:user entry))))))))
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
          (testing "application not approved"
            (let [application (api-call :get (str "/api/applications/" application-id) nil
                                        api-key applicant-id)]
              (is (= "application.state/submitted" (:application/state application)))))
          (testing "blacklist visible to handler in application"
            (let [application (api-call :get (str "/api/applications/" application-id) nil
                                        api-key handler-id)]
              (is (= {(keyword applicant-id) [resource-ext-id]} (:application/blacklisted-users application))))))))))
