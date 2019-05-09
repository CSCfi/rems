(ns rems.db.entitlements
  "Creating and fetching entitlements."
  (:require [clj-http.client :as http]
            [clojure.set :refer [union]]
            [clojure.string :refer [join]]
            [clojure.tools.logging :as log]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.application-util :refer [accepted-licenses?]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.roles :refer [has-roles?]]
            [rems.text :as text]
            [rems.util :refer [getx-user-id]]))

;; TODO move Entitlement schema here from rems.api?

(defn- entitlement-to-api [{:keys [resid catappid start mail]}]
  {:resource resid
   :application-id catappid
   :start (text/localize-time start)
   :mail mail})

(defn get-entitlements-for-api [user-or-nil resource-or-nil]
  (if (has-roles? :handler)
    (mapv entitlement-to-api (db/get-entitlements {:user user-or-nil
                                                   :resource resource-or-nil}))
    (mapv entitlement-to-api (db/get-entitlements {:user (getx-user-id)
                                                   :resource resource-or-nil}))))

(defn get-entitlements-for-export
  "Returns a CSV string representing entitlements"
  []
  (when-not (has-roles? :handler)
    (throw-forbidden))
  (let [ents (db/get-entitlements)
        separator (:csv-separator env)]
    (with-out-str
      (println (join separator ["resource" "application" "user" "start"]))
      (doseq [e ents]
        (println (join separator [(:resid e) (:catappid e) (:userid e) (text/localize-time (:start e))]))))))

(defn- post-entitlements [target-key entitlements]
  (when-let [target (get-in env [:entitlements-target target-key])]
    (let [payload (for [e entitlements]
                    {:application (:catappid e)
                     :resource (:resid e)
                     :user (:userid e)
                     :mail (:mail e)})
          json-payload (json/generate-string payload)]
      (log/infof "Posting entitlements to %s:" target payload)
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
        (when-not (= 200 status)
          (log/warnf "Post failed: %s", response))
        (db/log-entitlement-post! {:target target :payload json-payload :status status})))))

(defn update-entitlements-for
  "If the given application is approved, licenses accepted etc. add an entitlement to the db
  and call the entitlement REST callback (if defined). Likewise if a resource is removed, member left etc.
  then we end the entitlement and call the REST callback."
  [application]
  (when (contains? #{:application.state/approved :application.state/closed}
                   (:application/state application))
    (let [app-id (:application/id application)
          members (set (concat (map :userid (:application/members application))
                               [(:application/applicant application)]))
          past-members (set (map :userid (:application/past-members application)))
          application-resources (->> application
                                     :application/resources
                                     (map :resource/id)
                                     set)
          application-entitlements (->> (db/get-entitlements {:application app-id :is-active? true})
                                        (group-by :userid)
                                        (map (fn [[userid rows]]
                                               [userid (set (map :resourceid rows))]))
                                        (into {}))
          user-entitlements (into {}
                                  (for [userid (union members past-members)]
                                    [userid (or (application-entitlements userid) #{})]))
          entitlements-to-add (->> (for [[userid resource-ids] user-entitlements
                                         :when (and (= :application.state/approved (:application/state application))
                                                    (contains? members userid)
                                                    (accepted-licenses? application userid))
                                         resource-id application-resources
                                         :when (not (contains? resource-ids resource-id))]
                                     {userid #{resource-id}})
                                   (apply merge-with union))
          entitlements-to-remove (->> (for [[userid resource-ids] user-entitlements
                                            resource-id resource-ids
                                            :when (or (= :application.state/closed (:application/state application))
                                                      (not (contains? members userid))
                                                      (not (accepted-licenses? application userid))
                                                      (not (contains? application-resources resource-id)))]
                                        {userid #{resource-id}})
                                      (apply merge-with union))
          members-to-update (set
                             (concat (keys entitlements-to-add)
                                     (keys entitlements-to-remove)))]
      (when (seq members-to-update)
        (log/info "updating entitlements on application" app-id)
        (doseq [[userid resource-ids] entitlements-to-add]
          (log/info "granting entitlements on application" app-id "to" userid "resources" resource-ids)
          (doseq [resource-id (sort resource-ids)]
            (db/add-entitlement! {:application app-id
                                  :user userid
                                  :resource resource-id})
            (post-entitlements :add (db/get-entitlements {:application app-id :user userid :resource resource-id}))))
        (doseq [[userid resource-ids] entitlements-to-remove]
          (log/info "revoking entitlements on application" app-id "to" userid "resources" resource-ids)
          (doseq [resource-id (sort resource-ids)]
            (db/end-entitlements! {:application app-id
                                   :user userid
                                   :resource resource-id})
            (post-entitlements :remove (db/get-entitlements {:application app-id :user userid :resource resource-id})))))
      members-to-update)))
