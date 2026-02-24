(ns ^:integration rems.api.test-over-http
  "API tests that use a full HTTP server."
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [rems.api.testing :refer [standalone-fixture]]
            [rems.application.commands]
            [rems.config]
            [rems.db.api-key]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.email.core]
            [rems.json :as json]
            [rems.event-notification :as event-notification]
            [stub-http.core :as stub])
  (:import [java.net URL]))

(use-fixtures :each standalone-fixture)

(defn- create-test-data []
  (rems.db.api-key/add-api-key! 42 {:comment "test data"})
  (test-helpers/create-user! {:userid "handler"})
  (test-helpers/create-user! {:userid "applicant"})
  (test-helpers/create-user! {:userid "developer"})
  (let [wfid (test-helpers/create-workflow! {:handlers ["handler"]})
        form (test-helpers/create-form! nil)
        res-id1 (test-helpers/create-resource! nil)
        item-id1 (test-helpers/create-catalogue-item! {:form-id form :workflow-id wfid :resource-id res-id1})
        app-id (test-helpers/create-draft! "applicant" [item-id1] "draft")]
    (test-helpers/submit-application {:application-id app-id
                                      :actor "applicant"})))

(deftest test-api-sql-timeouts
  (create-test-data)
  (let [api-key "42"
        user-id "applicant"
        application-id (test-helpers/create-application! {:actor user-id})
        application-id-2 (test-helpers/create-application! {:actor user-id})
        save-draft! #(-> (http/post (str (:public-url rems.config/env) "/api/applications/save-draft")
                                    {:throw-exceptions false
                                     :as :json
                                     :headers {"x-rems-api-key" api-key
                                               "x-rems-user-id" user-id}
                                     :content-type :json
                                     :form-params {:application-id application-id
                                                   :field-values []}})
                         (select-keys [:body :status]))

        old-handle-command rems.application.commands/handle-command
        sleep-time (atom nil)
        sleeping-handle-command (fn [cmd application injections]
                                  (when-let [time @sleep-time]
                                    (Thread/sleep time))
                                  (old-handle-command cmd application injections))]
    (rems.email.core/try-send-emails!) ;; remove a bit of clutter from the log
    (with-redefs [rems.application.commands/handle-command sleeping-handle-command]
      (testing "lock_timeout"
        ;; more than the lock timeout of 4s, less than the connection idle timeout of 8s
        ;; these shorter-than-default timeouts are in dev-config.edn and test-config.edn
        (reset! sleep-time (* 6 1000))
        (let [commands [(future (save-draft!)) (future (save-draft!))]]
          (is (= #{{:status 200 :body {:success true}}
                   {:status 503 :body "please try again"}}
                 (set (mapv deref commands))))))
      (testing "idle_in_transaction_session_timeout"
        (testing "slow transaction should hit timeout"
          ;; more than the connection idle timeout of 8s
          (reset! sleep-time (* 10 1000))
          (is (= {:status 500 :body ""}
                 (save-draft!))))
        (testing "subsequent transactions should pass"
          (reset! sleep-time nil)
          (is (= {:status 200 :body {:success true}}
                 (save-draft!))))))))

(deftest test-allocate-external-id
  (create-test-data)
  ;; this test mimics an external id number service
  (with-open [server (stub/start! {"/" (fn [r]
                                         (let [event (json/parse-string (get-in r [:body "content"]))
                                               app-id (:application/id event)
                                               response (http/post (str (:public-url rems.config/env) "/api/applications/assign-external-id")
                                                                   {:as :json
                                                                    :headers {"x-rems-api-key" "42"
                                                                              "x-rems-user-id" "developer"}
                                                                    :content-type :json
                                                                    :form-params {:application-id app-id
                                                                                  :external-id "new-id"}})]
                                           (assert (get-in response [:body :success])))
                                         {:status 200})})]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :event-notification-targets [{:url (:uri server)
                                                                       :event-types [:application.event/submitted]}])]
      (let [api-key "42"
            applicant "applicant"
            cat-id (test-helpers/create-catalogue-item! {})
            app-id (test-helpers/create-draft! applicant [cat-id] "value")
            get-ext-id #(-> (http/get (str (:public-url rems.config/env) "/api/applications/" app-id)
                                      {:as :json
                                       :headers {"x-rems-api-key" api-key
                                                 "x-rems-user-id" applicant}})
                            (get-in [:body :application/external-id]))]
        (event-notification/process-outbox!)
        (is (empty? (stub/recorded-requests server)))
        (is (not= "new-id" (get-ext-id)))
        (is (-> (http/post (str (:public-url rems.config/env) "/api/applications/submit")
                           {:as :json
                            :headers {"x-rems-api-key" api-key
                                      "x-rems-user-id" applicant}
                            :content-type :json
                            :form-params {:application-id app-id}})
                (get-in [:body :success])))
        (event-notification/process-outbox!)
        (is (= 1 (count (stub/recorded-responses server))))
        (is (= "new-id" (get-ext-id)))))))

(deftest test-jwks
  (is (= "2011-04-29" (-> (http/get (str (:public-url rems.config/env) "api/jwk")
                                    {:as :json})
                          :body
                          :keys
                          first
                          :kid))))

(deftest test-api-audit-log
  (create-test-data)
  (test-helpers/create-user! {:userid "reporter"} :reporter)

  (testing "populate audit log with a 404 not found"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"clj-http: status 404"
                          (http/get "http://localhost:3001/api/unknown"))))

  (testing "response status is 200 OK"
    (let [{:keys [status body]} (http/get (str (:public-url rems.config/env) "/api/audit-log")
                                          {:as :json
                                           :headers {"x-rems-api-key" "42"
                                                     "x-rems-user-id" "reporter"}})]
      (is (= 200 status))
      (is (= [{:apikey nil
               :method "get"
               :path "/api/unknown"
               :status "404"
               :userid nil}]
             (mapv #(dissoc % :time) body))))))
