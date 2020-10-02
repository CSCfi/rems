(ns ^:integration rems.api.test-over-http
  "API tests that use a full HTTP server."
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [rems.api.testing :refer [standalone-fixture]]
            [rems.config]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.json :as json]
            [rems.event-notification :as event-notification]
            [stub-http.core :as stub])
  (:import [com.auth0.jwk JwkProviderBuilder]
           [java.net URL]))

(use-fixtures :each standalone-fixture)

(deftest test-api-sql-timeouts
  (let [api-key "42"
        user-id "alice"
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
          (is (= {:status 500 :body "{\"type\":\"unknown-exception\",\"class\":\"clojure.lang.ExceptionInfo\"}"}
                 (save-draft!))))
        (testing "subsequent transactions should pass"
          (reset! sleep-time nil)
          (is (= {:status 200 :body {:success true}}
                 (save-draft!))))))))

(deftest test-allocate-external-id
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
            applicant "alice"
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
  (is (some? (-> (str (:public-url rems.config/env) "api/jwk")
                 (URL.)
                 (JwkProviderBuilder.)
                 (.build)
                 (.get "2011-04-29")
                 (.getPublicKey)))))
