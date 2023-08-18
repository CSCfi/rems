(ns rems.service.command
  (:require [clojure.tools.logging :as log]
            [rems.application.approver-bot :as approver-bot]
            [rems.application.bona-fide-bot :as bona-fide-bot]
            [rems.application.process-managers :as process-managers]
            [rems.application.rejecter-bot :as rejecter-bot]
            [rems.application.search :as search]
            [rems.config]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.blacklist :as blacklist]
            [rems.db.catalogue :as catalogue]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.db.user-mappings :as user-mappings]
            [rems.db.workflow :as workflow]
            [rems.email.core :as email]
            [rems.entitlements :as entitlements]
            [rems.event-notification :as event-notification]
            [rems.service.cache :as cache]
            [rems.util :refer [secure-token]]))

;; Process managers react to events with side effects & new commands.
;; See docs/architecture/002-event-side-effects.md
(defn- run-process-managers [new-events]
  (doall
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
    (process-managers/clear-redacted-attachments new-events)
    (search/request-index-events! new-events cache/get-full-internal-application))))


(def ^:private command-injections
  {:get-attachments-for-application attachments/get-attachments-for-application
   :get-form-template form/get-form-template
   :get-catalogue-item catalogue/get-expanded-catalogue-item
   :get-config (fn [] rems.config/env)
   :get-license licenses/get-license
   :get-user users/get-user
   :get-users-with-role users/get-users-with-role
   :get-workflow workflow/get-workflow
   :blacklisted? blacklist/blacklisted?
   :get-attachment-metadata attachments/get-attachment-metadata
   :get-catalogue-item-licenses cache/get-catalogue-item-licenses
   :secure-token secure-token
   :allocate-application-ids! applications/allocate-application-ids!
   :copy-attachment! attachments/copy-attachment!
   :valid-user? users/user-exists?
   :find-userid user-mappings/find-userid})

(def ^:dynamic *fail-on-process-manager-errors* false)

(defn command! [cmd]
  (applications/one-at-a-time!
   (let [application (when-let [app-id (:application-id cmd)]
                       (cache/get-full-internal-application app-id))
         result (applications/command! cmd
                                       application
                                       command-injections)]
     (if-not (:errors result)
       (let [events-from-db (mapv #(applications/add-event-with-compaction! application %)
                                  (:events result))]
         (doseq [cmd2 (run-process-managers events-from-db)]
           (let [result (command! cmd2)]
             (when (:errors result)
               (if *fail-on-process-manager-errors*
                 (assert false
                         (pr-str {:cmd cmd2 :result result :parent-cmd cmd}))
                 (log/error "process manager command failed"
                            (pr-str {:cmd cmd2 :result result :parent-cmd cmd}))))))
         (assoc result :events events-from-db)) ; replace with events with id
       result))))
