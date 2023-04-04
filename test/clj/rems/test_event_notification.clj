(ns ^:integration rems.test-event-notification
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [medley.core :refer [dissoc-in]]
            [rems.config]
            [rems.service.command :as command]
            [rems.api.testing :refer [api-fixture api-call]]
            [rems.db.events]
            [rems.service.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.event-notification :as event-notification]
            [rems.json :as json]
            [stub-http.core :as stub]))

(use-fixtures
  :each
  api-fixture)

(deftest test-notify!
  (with-open [server (stub/start! {"/ok" {:status 200}
                                   "/broken" {:status 500}
                                   "/timeout" {:status 200 :delay 5000}})]
    (let [body {:value 1}]
      (testing "success"
        (is (nil? (#'event-notification/notify! {:url (str (:uri server) "/ok")
                                                 :headers {"additional-header" "value"}}
                                                body)))
        (let [[req & more] (stub/recorded-requests server)]
          (is (empty? more))
          (is (= {:method "PUT"
                  :path "/ok"
                  :body {"content" (json/generate-string body)}}
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
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (with-open [server (stub/start! {"/created" {:status 200}
                                   "/all" {:status 200}
                                   "/no-application" {:status 200}})]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :enable-save-compaction true
                                         :event-notification-targets [{:url (str (:uri server) "/created")
                                                                       :event-types [:application.event/created]}
                                                                      {:url (str (:uri server) "/all")}
                                                                      {:url (str (:uri server) "/no-application")
                                                                       :send-application? false}])]
      (let [get-notifications #(doall
                                (for [r (stub/recorded-requests server)]
                                  {:path (:path r)
                                   :data (-> r
                                             :body
                                             (get "content")
                                             json/parse-string)}))
            form-id (test-helpers/create-form! {:form/internal-name "notifications"
                                                :form/external-title {:en "Notifications EN"
                                                                      :fi "Notifications FI"
                                                                      :sv "Notifications SV"}
                                                :form/fields [{:field/type :text
                                                               :field/id "field-1"
                                                               :field/title {:en "text field"
                                                                             :fi "tekstikenttä"
                                                                             :sv "textfält"}
                                                               :field/optional false}]})
            handler "handler"
            workflow-id (test-helpers/create-workflow! {:title "wf"
                                                        :handlers [handler]
                                                        :type :workflow/default})
            ext-id "resres"
            res-id (test-helpers/create-resource! {:resource-ext-id ext-id})
            cat-id (test-helpers/create-catalogue-item! {:form-id form-id
                                                         :resource-id res-id
                                                         :workflow-id workflow-id})
            applicant "alice"
            app-id (:application-id (command/command! {:type :application.command/create
                                                       :actor applicant
                                                       :time (time/date-time 2001)
                                                       :catalogue-item-ids [cat-id]}))
            event-id (:event/id (first (rems.db.events/get-application-events app-id)))]
        (testing "no notifications before outbox is processed"
          (is (empty? (stub/recorded-requests server))))

        (event-notification/process-outbox!)

        (testing "created event gets sent to all endpoints"
          (let [notifications (get-notifications)
                app-from-raw-api (api-call :get (str "/api/applications/" app-id "/raw") nil
                                           "42" "reporter")]
            (is (= 3 (count notifications)))
            (is (= #{"/created" "/all" "/no-application"}
                   (set (map :path notifications))))
            (is (= {:application/external-id "2001/1"
                    :application/id app-id
                    :event/id event-id
                    :event/time "2001-01-01T00:00:00.000Z"
                    :workflow/type "workflow/default"
                    :application/resources [{:resource/ext-id ext-id
                                             :catalogue-item/id cat-id}]
                    :application/forms [{:form/id form-id}]
                    :workflow/id workflow-id
                    :event/actor applicant
                    :event/type "application.event/created"
                    :application/licenses []
                    :event/application app-from-raw-api}
                   (:data (first notifications))))
            (is (= (:data (first notifications))
                   (:data (second notifications))))))

        (let [event (-> {:application-id app-id
                         :type :application.command/save-draft
                         :actor applicant
                         :time (time/date-time 2001)
                         :field-values [{:form form-id :field "field-1" :value "my value"}]}
                        command/command!
                        :events
                        first)]

          (event-notification/process-outbox!)

          (testing "draft-saved event gets sent to /all and /no-application"
            (let [requests (get-notifications)
                  ;; the requests are sorted by path
                  all-request (nth requests 2)
                  no-app-request (nth requests 4)]
              (is (= 5 (count requests)))
              (is (= "/all" (:path all-request)))
              (is (= "/no-application" (:path no-app-request)))
              (is (= "application.event/draft-saved" (get-in all-request [:data :event/type])))
              (is (= "application.event/draft-saved" (get-in no-app-request [:data :event/type])))
              (is (= (:event/id event)
                     (:event/id (:data all-request))
                     (:event/id (:data no-app-request))))
              (is (= "my value"
                     (-> all-request
                         :data
                         :application/field-values
                         first
                         :value)))
              (is (contains? (:data all-request) :event/application))
              (is (not (contains? (:data no-app-request) :event/application))))))

        ;; another save gets sent even with compaction
        (testing "another (compacted) save"
          (let [event (-> {:application-id app-id
                           :type :application.command/save-draft
                           :actor applicant
                           :time (time/date-time 2001)
                           :field-values [{:form form-id :field "field-1" :value "new value"}]}
                          command/command!
                          :events
                          first)]

            (event-notification/process-outbox!)

            (testing "draft-saved event gets sent only to /all and /no-application"
              (let [requests (get-notifications)
                    ;; the requests are sorted by path
                    all-request (nth requests 3)
                    no-app-request (nth requests 6)]
                (is (= 7 (count requests)))
                (is (= "/all" (:path all-request)))
                (is (= "/no-application" (:path no-app-request)))
                (is (= "application.event/draft-saved" (get-in all-request [:data :event/type])))
                (is (= "application.event/draft-saved" (get-in no-app-request [:data :event/type])))
                (is (= (:event/id event)
                       (:event/id (:data all-request))
                       (:event/id (:data no-app-request))))
                (is (= "new value"
                       (-> all-request
                           :data
                           :application/field-values
                           first
                           :value)))
                (is (contains? (:data all-request) :event/application))
                (is (not (contains? (:data no-app-request) :event/application)))))))))))

(deftest test-event-notification-ordering
  (test-data/create-test-api-key!)
  (test-data/create-test-users-and-roles!)
  (with-open [server (stub/start! {"/" {:status 200}})]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :event-notification-targets [{:url (str (:uri server) "/")}])]
      (let [get-notifications #(doall
                                (for [r (stub/recorded-requests server)]
                                  (-> r
                                      :body
                                      (get "content")
                                      json/parse-string
                                      (select-keys [:event/id :event/time]))))
            form-id (test-helpers/create-form! {:form/internal-name "notifications"
                                                :form/external-title {:en "Notifications EN"
                                                                      :fi "Notifications FI"
                                                                      :sv "Notifications SV"}
                                                :form/fields [{:field/type :text
                                                               :field/id "field-1"
                                                               :field/title {:en "text field"
                                                                             :fi "tekstikenttä"
                                                                             :sv "textfält"}
                                                               :field/optional false}]})
            cat-id (test-helpers/create-catalogue-item! {:form-id form-id})
            applicant "alice"
            t (time/date-time 2010)
            app-id (:application-id (command/command! {:type :application.command/create
                                                       :actor applicant
                                                       :time t
                                                       :catalogue-item-ids [cat-id]}))]
        (dotimes [i 100]
          (command/command! {:type :application.command/save-draft
                             :actor applicant
                             :application-id app-id
                             :time (time/plus t (time/seconds i))
                             :field-values [{:form form-id :field "field-1" :value (str i)}]}))
        (event-notification/process-outbox!)
        (let [notifications (get-notifications)]
          (is (apply < (mapv :event/id notifications))))))))
