(ns rems.api.services.command
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [rems.api.services.blacklist :as blacklist]
            [rems.application.approver-bot :as approver-bot]
            [rems.application.commands :as commands]
            [rems.application.rejecter-bot :as rejecter-bot]
            [rems.common.application-util :as application-util]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.entitlements :as entitlements]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.email.core :as email]
            [rems.event-notification :as event-notification]
            [rems.form-validation :as form-validation]
            [rems.util :refer [secure-token]])
  (:import rems.TryAgainException))

(defn- run-entitlements [new-events]
  (doseq [event new-events]
    (entitlements/update-entitlements-for-event event))
  [])

(defn- revokes-to-blacklist [new-events]
  (doseq [event new-events]
    (when (= :application.event/revoked (:event/type event))
      (let [application (applications/get-application-internal (:application/id event))]
        (doseq [resource (:application/resources application)]
          (blacklist/add-users-to-blacklist! {:users (application-util/applicant-and-members application)
                                              :resource/ext-id (:resource/ext-id resource)
                                              :actor (:event/actor event)
                                              :comment (:application/comment event)})))))
  [])

(defn run-process-managers [new-events]
  (concat
   (revokes-to-blacklist new-events)
   (email/generate-event-emails! new-events)
   (run-entitlements new-events)
   (rejecter-bot/run-rejecter-bot new-events)
   (approver-bot/run-approver-bot new-events)
   (event-notification/queue-notifications! new-events)))

(def ^:private command-injections
  (merge applications/fetcher-injections
         {:valid-user? users/user-exists? ;; TODO move to fetcher-injections
          :secure-token secure-token
          :get-catalogue-item-licenses applications/get-catalogue-item-licenses ;; TODO move to fetcher-injections
          :allocate-application-ids! applications/allocate-application-ids!
          :get-attachment-metadata attachments/get-attachment-metadata ;; TODO move to fetcher-injections
          :copy-attachment! attachments/copy-attachment!}))

(defn command! [cmd]
  ;; Use locks to prevent multiple commands being executed in parallel.
  ;; Serializable isolation level will already avoid anomalies, but produces
  ;; lots of transaction conflicts when there is contention. This lock
  ;; roughly doubles the throughput for rems.db.test-transactions tests.
  ;;
  ;; To clarify: without this lock our API calls would sometimes fail
  ;; with transaction conflicts due to the serializable isolation
  ;; level. The transaction conflict exceptions aren't handled
  ;; currently and would cause API calls to fail with HTTP status 500.
  ;; With this lock, API calls block more but never result in
  ;; transaction conflicts.
  ;;
  ;; See docs/architecture/010-transactions.md for more info.
  (try
    (jdbc/execute! db/*db* ["LOCK TABLE application_event IN SHARE ROW EXCLUSIVE MODE"])
    (catch org.postgresql.util.PSQLException e
      (if (.contains (.getMessage e) "lock timeout")
        (throw (TryAgainException. e))
        (throw e))))
  (let [app (when-let [app-id (:application-id cmd)]
              (applications/get-application-internal app-id))
        result (commands/handle-command cmd app command-injections)]
    (when-not (:errors result)
      (let [events-from-db (mapv events/add-event! (:events result))]
        (doseq [cmd2 (run-process-managers events-from-db)]
          (let [result (command! cmd2)]
            (when (:errors result)
              (log/error "process manager command failed"
                         (pr-str {:cmd cmd2 :result result :parent-cmd cmd})))))))
    result))
