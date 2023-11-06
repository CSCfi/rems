(ns rems.entitlements
  (:require [clj-http.client :as http]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.outbox :as outbox]
            [rems.scheduler :as scheduler]
            [rems.ga4gh :as ga4gh]
            [rems.json :as json]
            [rems.plugins :as plugins]
            [rems.service.application :as application]
            [rems.service.ega :as ega]
            [rems.service.entitlements :as entitlements]))

(defn- get-entitlements-payload [entitlements action]
  (case action
    :ga4gh (ga4gh/entitlements->passport entitlements)
    (for [e entitlements]
      {:application (:catappid e)
       :resource (:resid e)
       :user (:userid e)
       :mail (:mail e)
       :end (:end e)})))

(defn- post-entitlements! [{:keys [action type entitlements config]}]
  (case type
    :plugin
    (plugins/process :extension-point/process-entitlements entitlements)

    ;; TODO consider removing in favour of plugins
    :ega
    (when config
      (try (doseq [entitlement entitlements] ; technically these could be grouped per user & api-key
             (ega/entitlement-push action entitlement config))
           (catch Exception e
             (log/error "POST failed" e)
             (or (ex-data e)
                 [{:status "exception"}]))))

    ;; TODO consider removing in favour of plugins
    :basic ; TODO: let's move this :entitlements-target (v1) at some point to :entitlement-post (v2)
    (when-let [target (get-in env [:entitlements-target action])]
      (let [payload (get-entitlements-payload entitlements action)
            json-payload (json/generate-string payload)]
        (log/infof "Posting entitlements to %s: %s" target payload)
        (let [response (try
                         (http/post target
                                    {:throw-exceptions false
                                     :body json-payload
                                     :content-type :json
                                     :socket-timeout 2500
                                     :conn-timeout 2500})
                         (catch Exception e
                           (log/error "POST failed" e)
                           {:status "exception"}))
              status (:status response)]

          (if (= 200 status)
            (log/infof "Posted entitlements to %s: %s -> %s" target payload status)
            (do
              (log/warnf "Entitlement post failed: %s", response)
              [(str "failed: " status)])))))))

;; TODO argh adding these everywhere sucks
;; TODO consider using schema coercions
(defn- fix-entry-from-db [entry]
  (-> entry
      (update-in [:outbox/entitlement-post :action] keyword)
      (update-in [:outbox/entitlement-post :type] keyword)
      (update-in [:outbox/entitlement-post :config :type] keyword)))

(defn process-outbox! []
  (doseq [entry (mapv fix-entry-from-db
                      (outbox/get-due-entries :entitlement-post))]
    ;; TODO could send multiple entitlements at once instead of one outbox entry at a time
    (if-let [errors (post-entitlements! (:outbox/entitlement-post entry))]
      (let [entry (outbox/attempt-failed! entry errors)]
        (when (not (:outbox/next-attempt entry))
          (log/warn "all attempts to send entitlement post id " (:outbox/id entry) "failed")))
      (outbox/attempt-succeeded! (:outbox/id entry)))))

(mount/defstate entitlement-poller
  :start (scheduler/start! "entitlement-poller" process-outbox! (.toStandardDuration (time/seconds 10)))
  :stop (scheduler/stop! entitlement-poller))

(defn update-entitlements-for-event [event]
  ;; performance improvement: filter events which may affect entitlements
  (when (contains? #{:application.event/approved
                     :application.event/closed
                     :application.event/licenses-accepted
                     :application.event/member-removed
                     :application.event/resources-changed
                     :application.event/revoked}
                   (:event/type event))
    (let [application (application/get-full-internal-application (:application/id event))]
      (entitlements/update-entitlements-for-application application (:event/actor event)))))

(defn update-entitlements-for-events [events]
  (doseq [event events]
    (update-entitlements-for-event event))
  ;; this is used as a process manager, so return an explicit empty vector of commands
  [])

