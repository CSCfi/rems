(ns rems.db.entitlements
  "Creating and fetching entitlements."
  (:require [clj-http.client :as http]
            [clj-time.core :as time]
            [clojure.set :refer [union]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.common.application-util :as application-util]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.outbox :as outbox]
            [rems.ga4gh :as ga4gh]
            [rems.json :as json]
            [rems.scheduler :as scheduler]
            [rems.service.ega :as ega]))

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
    :ega
    (when config
      (try (doseq [entitlement entitlements] ; technically these could be grouped per user & api-key
             (ega/entitlement-push action entitlement config))
           (catch Exception e
             (log/error "POST failed" e)
             (or (ex-data e)
                 {:status "exception"}))))

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
              (str "failed: " status))))))))

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
    (if-let [error (post-entitlements! (:outbox/entitlement-post entry))]
      (let [entry (outbox/attempt-failed! entry error)]
        (when (not (:outbox/next-attempt entry))
          (log/warn "all attempts to send entitlement post id " (:outbox/id entry) "failed")))
      (outbox/attempt-succeeded! (:outbox/id entry)))))

(mount/defstate entitlement-poller
  :start (scheduler/start! "entitlement-poller" process-outbox! (.toStandardDuration (time/seconds 10)))
  :stop (scheduler/stop! entitlement-poller))

(defn- add-to-outbox! [action type entitlements config]
  (outbox/put! {:outbox/type :entitlement-post
                :outbox/deadline (time/plus (time/now) (time/days 1)) ;; hardcoded for now
                :outbox/entitlement-post {:action action
                                          :type type
                                          :entitlements entitlements
                                          :config config}}))

(defn- grant-entitlements! [application-id user-id resource-ids actor end]
  (log/info "granting entitlements on application" application-id "to" user-id "resources" resource-ids "until" end)
  (doseq [resource-id (sort resource-ids)]
    (db/add-entitlement! {:application application-id
                          :user user-id
                          :resource resource-id
                          :approvedby actor
                          :start (time/now) ; TODO should ideally come from the command time
                          :end end})
    ;; TODO could generate only one outbox entry per application. Currently one per user-resource pair.
    (let [entitlements (db/get-entitlements {:application application-id :user user-id :resource resource-id})]
      (add-to-outbox! :add :basic entitlements nil)
      (doseq [config (:entitlement-push env)]
        (add-to-outbox! :add (:type config) entitlements config)))))

(defn- revoke-entitlements! [application-id user-id resource-ids actor end]
  (log/info "revoking entitlements on application" application-id "to" user-id "resources" resource-ids "at" end)
  (doseq [resource-id (sort resource-ids)]
    (db/end-entitlements! {:application application-id
                           :user user-id
                           :resource resource-id
                           :revokedby actor
                           :end end})
    (let [entitlements (db/get-entitlements {:application application-id :user user-id :resource resource-id})]
      (add-to-outbox! :remove :basic entitlements nil)
      (doseq [config (:entitlement-push env)]
        (add-to-outbox! :remove (:type config) entitlements config)))))

(defn- get-entitlements-by-user [application-id]
  (->> (db/get-entitlements {:application application-id :active-at (time/now)})
       (group-by :userid)
       (map (fn [[userid rows]]
              [userid (set (map :resourceid rows))]))
       (into {})))

(defn- update-entitlements-for-application
  "If the given application is approved, licenses accepted etc. add an entitlement to the db
  and call the entitlement REST callback (if defined). Likewise if a resource is removed, member left etc.
  then we end the entitlement and call the REST callback."
  [application actor]
  (let [application-id (:application/id application)
        current-members (set (map :userid (application-util/applicant-and-members application)))
        past-members (set (map :userid (:application/past-members application)))
        application-state (:application/state application)
        application-resources (->> application
                                   :application/resources
                                   (map :resource/id)
                                   set)
        application-entitlements (get-entitlements-by-user application-id)
        is-entitled? (fn [userid resource-id]
                       (and (= :application.state/approved application-state)
                            (contains? current-members userid)
                            (application-util/accepted-licenses? application userid)
                            (contains? application-resources resource-id)))
        entitlements-by-user (fn [userid] (or (application-entitlements userid) #{}))
        entitlements-to-add (->> (for [userid (union current-members past-members)
                                       :let [current-resource-ids (entitlements-by-user userid)]
                                       resource-id application-resources
                                       :when (is-entitled? userid resource-id)
                                       :when (not (contains? current-resource-ids resource-id))]
                                   {userid #{resource-id}})
                                 (apply merge-with union))
        entitlements-to-remove (->> (for [userid (union current-members past-members)
                                          :let [resource-ids (entitlements-by-user userid)]
                                          resource-id resource-ids
                                          :when (not (is-entitled? userid resource-id))]
                                      {userid #{resource-id}})
                                    (apply merge-with union))
        members-to-update (keys (merge entitlements-to-add entitlements-to-remove))]
    (when (seq members-to-update)
      (log/info "updating entitlements on application" application-id)
      (doseq [[userid resource-ids] entitlements-to-add]
        (grant-entitlements! application-id userid resource-ids actor (:entitlement/end application)))
      (doseq [[userid resource-ids] entitlements-to-remove]
        ;; TODO should get the time from the event
        (revoke-entitlements! application-id userid resource-ids actor (time/now))))))

(defn update-entitlements-for-event [event]
  ;; performance improvement: filter events which may affect entitlements
  (when (contains? #{:application.event/approved
                     :application.event/closed
                     :application.event/licenses-accepted
                     :application.event/member-removed
                     :application.event/resources-changed
                     :application.event/revoked}
                   (:event/type event))
    (let [application (applications/get-application-internal (:application/id event))]
      (update-entitlements-for-application application (:event/actor event)))))

(defn update-entitlements-for-events [events]
  (doseq [event events]
    (update-entitlements-for-event event))
  ;; this is used as a process manager, so return an explicit empty vector of commands
  [])
