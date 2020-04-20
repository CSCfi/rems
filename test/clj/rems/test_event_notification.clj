(ns ^:integration rems.test-event-notification
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [medley.core :refer [dissoc-in]]
            [rems.config]
            [rems.api.services.command :as command]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.event-notification :as event-notification]
            [rems.json :as json]
            [rems.testing-util :refer [fixed-time-fixture with-user]]
            [stub-http.core :as stub]))

(use-fixtures
  :once
  test-db-fixture
  rollback-db-fixture)

(deftest test-notify!
  (with-open [server (stub/start! {"/ok" {:status 200}
                                   "/broken" {:status 500}
                                   "/timeout" {:status 200 :delay 5000}})]
    (let [body "body"]
      (testing "success"
        (is (nil? (#'event-notification/notify! {:url (str (:uri server) "/ok")
                                                 :headers {"additional-header" "value"}}
                                                body)))
        (let [[req & more] (stub/recorded-requests server)]
          (is (empty? more))
          (is (= {:method "PUT"
                  :path "/ok"
                  :body {"content" body}}
                 (select-keys req [:method :path :body])))
          (is (= "value" (get-in req [:headers :additional-header])))))
      (testing "error code"
        (is (= "failed: 500" (#'event-notification/notify! {:url (str (:uri server) "/broken")}
                                                           body))))
      (testing "timeout"
        (is (= "failed: exception" (#'event-notification/notify! {:url (str (:uri server) "/timeout")
                                                                  :timeout 1}
                                                                 body))))
      (testing "invalid url"
        (is (= "failed: exception" (#'event-notification/notify! {:url "http://invalid/lol"}
                                                                 body)))))))

(deftest test-event-notification
  ;; this is an integration test from commands to notifications
  (with-open [server (stub/start! {"/created" {:status 200}
                                   "/all" {:status 200}})]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :event-notification-targets [{:url (str (:uri server) "/created")
                                                                       :event-types [:application.event/created]}
                                                                      {:url (str (:uri server) "/all")}])]
      (let [get-notifications #(doall
                                (for [r (stub/recorded-requests server)]
                                  {:path (:path r)
                                   :data (-> r
                                             :body
                                             (get "content")
                                             json/parse-string
                                             (dissoc-in [:event/application :application/events])
                                             ;; catalogue-item/start is set by the db and can't be easily fixed
                                             (dissoc-in [:event/application :application/resources 0 :catalogue-item/start]))}))
            form-id (test-data/create-form! {:form/title "notifications"
                                             :form/fields [{:field/type :text
                                                            :field/id "field-1"
                                                            :field/title {:en "text field"}
                                                            :field/optional false}]})
            handler "handler"
            workflow-id (test-data/create-workflow! {:title "wf"
                                                     :handlers [handler]
                                                     :type :workflow/default})
            ext-id "resres"
            res-id (test-data/create-resource! {:resource-ext-id ext-id})
            cat-id (test-data/create-catalogue-item! {:form-id form-id
                                                      :resource-id res-id
                                                      :workflow-id workflow-id})
            applicant "alice"
            app-id (:application-id (command/command! {:type :application.command/create
                                                       :actor applicant
                                                       :time (time/date-time 2001)
                                                       :catalogue-item-ids [cat-id]}))]
        (testing "no notifications before outbox is processed"
          (is (empty? (stub/recorded-requests server))))
        (event-notification/process-outbox!)
        (testing "created event gets sent to both endpoints"
          (let [notifications (get-notifications)]
            (is (= 2 (count notifications)))
            (is (= #{"/created" "/all"}
                   (set (map :path notifications))))
            (is (= {:application/external-id "2001/1"
                    :application/id app-id
                    :event/time "2001-01-01T00:00:00.000Z"
                    :workflow/type "workflow/default"
                    :application/resources [{:resource/ext-id ext-id
                                             :catalogue-item/id cat-id}]
                    :application/forms [{:form/id form-id}]
                    :workflow/id workflow-id
                    :event/actor applicant
                    :event/type "application.event/created"
                    :application/licenses []
                    :event/application {:application/description ""
                                        :application/invited-members []
                                        :application/last-activity "2001-01-01T00:00:00.000Z"
                                        :application/attachments []
                                        :application/licenses []
                                        :application/created "2001-01-01T00:00:00.000Z"
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
                                                     "application.command/save-draft"
                                                     "application.command/close"
                                                     "application.command/change-resources"]}
                                        :application/modified "2001-01-01T00:00:00.000Z"
                                        :application/user-roles {:alice ["applicant"] :handler ["handler"]}
                                        :application/external-id "2001/1"
                                        :application/workflow {:workflow/type "workflow/default"
                                                               :workflow/id workflow-id
                                                               :workflow.dynamic/handlers
                                                               [{:email nil :userid "handler" :name nil}]}
                                        :application/blacklist []
                                        :application/id app-id
                                        :application/todo nil
                                        :application/applicant {:email nil :userid "alice" :name nil}
                                        :application/members []
                                        :application/resources [{:catalogue-item/end nil
                                                                 :catalogue-item/expired false
                                                                 :catalogue-item/enabled true
                                                                 :resource/id res-id
                                                                 :catalogue-item/title {}
                                                                 :catalogue-item/infourl {}
                                                                 :resource/ext-id ext-id
                                                                 :catalogue-item/archived false
                                                                 :catalogue-item/id cat-id}]
                                        :application/accepted-licenses {}
                                        :application/forms [{:form/fields [{:field/value ""
                                                                            :field/type "text"
                                                                            :field/title {:en "text field"}
                                                                            :field/id "field-1"
                                                                            :field/optional false
                                                                            :field/visible true}]
                                                             :form/title "notifications"
                                                             :form/id form-id}]}}
                   (:data (first notifications))))
            (is (= (:data (first notifications))
                   (:data (second notifications))))))
        (command/command! {:application-id app-id
                           :type :application.command/save-draft
                           :actor applicant
                           :time (time/date-time 2001)
                           :field-values [{:form form-id :field "field-1" :value "my value"}]})
        (event-notification/process-outbox!)
        (testing "draft-saved event gets sent only to /all"
          (let [requests (get-notifications)
                req (last requests)]
            (is (= 3 (count requests)))
            (is (= "/all" (:path req)))
            (is (= "application.event/draft-saved"
                   (:event/type (:data req))))))))))
