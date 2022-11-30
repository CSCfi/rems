(ns rems.service.command
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [rems.application.approver-bot :as approver-bot]
            [rems.application.bona-fide-bot :as bona-fide-bot]
            [rems.application.commands :as commands]
            [rems.application.process-managers :as process-managers]
            [rems.application.rejecter-bot :as rejecter-bot]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.entitlements :as entitlements]
            [rems.db.users :as users]
            [rems.db.user-mappings :as user-mappings]
            [rems.email.core :as email]
            [rems.event-notification :as event-notification]
            [rems.util :refer [secure-token]])
  (:import rems.TryAgainException))

;; Process managers react to events with side effects & new commands.
;; See docs/architecture/002-event-side-effects.md
(defn run-process-managers [new-events]
  (concat
   (process-managers/revokes-to-blacklist new-events)
   (email/generate-event-emails! new-events)
   (entitlements/update-entitlements-for-events new-events)
   (rejecter-bot/run-rejecter-bot new-events)
   (approver-bot/run-approver-bot new-events)
   (bona-fide-bot/run-bona-fide-bot new-events)
   (event-notification/queue-notifications! new-events)
   (process-managers/delete-applications new-events)
   (process-managers/delete-orphan-attachments-on-submit new-events)
   (process-managers/clear-redacted-attachments new-events)))

(def ^:private command-injections
  (merge applications/fetcher-injections
         {:secure-token secure-token
          :allocate-application-ids! applications/allocate-application-ids!
          :copy-attachment! attachments/copy-attachment!
          :valid-user? users/user-exists?
          :find-userid user-mappings/find-userid}))

(def ^:dynamic *fail-on-process-manager-errors* false)

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
    (if-not (:errors result)
      (let [events-from-db (mapv #(events/add-event-with-compaction! app %) (:events result))]
        (doseq [cmd2 (run-process-managers events-from-db)]
          (let [result (command! cmd2)]
            (when (:errors result)
              (if *fail-on-process-manager-errors*
                (assert false
                        (pr-str {:cmd cmd2 :result result :parent-cmd cmd}))
                (log/error "process manager command failed"
                           (pr-str {:cmd cmd2 :result result :parent-cmd cmd}))))))
        (assoc result :events events-from-db)) ; replace with events with id
      result)))
