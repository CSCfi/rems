(ns ^:integration rems.api.test-over-http
  "API tests that use a full HTTP server."
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [rems.api.testing :refer [standalone-fixture]]
            [rems.config]
            [rems.db.test-data :as test-data]))

(use-fixtures :once standalone-fixture)

(deftest test-api-sql-timeouts
  (let [api-key "42"
        user-id "alice"
        application-id (test-data/create-application! {:actor user-id})
        application-id-2 (test-data/create-application! {:actor user-id})
        save-draft! #(-> (http/post (str (:public-url rems.config/env) "/api/applications/save-draft")
                                    {:throw-exceptions false
                                     :as :json
                                     :headers {"x-rems-api-key" "42"
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
