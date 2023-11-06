(ns rems.service.entitlements
  (:require [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [clojure.set :refer [union]]
            [rems.common.application-util :as application-util]
            [rems.config :refer [env]]
            [rems.csv :refer [print-to-csv]]
            [rems.db.entitlements :as entitlements]
            [rems.db.outbox :as outbox]
            [rems.db.users :refer [join-user]]))

(defn- format-entitlement [{:keys [resid catappid userid start end mail]}]
  {:resource resid
   :application-id catappid
   :userid userid
   :start start
   :end end
   :mail mail})

(defn- join-dependencies [entitlement]
  (-> entitlement
      (assoc :user (join-user entitlement))
      (dissoc :userid)))

(defn get-entitlements-for-api [{:keys [user-id resource-ext-id expired]}]
  (->> (entitlements/get-entitlements {:user user-id
                                       :resource-ext-id resource-ext-id
                                       :active-at (when-not expired
                                                    (time/now))})
       (mapv (comp join-dependencies format-entitlement))))

(defn- get-entitlement-csv-format []
  (->> ["resource" :resid
        "application" :catappid
        "user" :userid
        "start" :start]
       (partition 2)
       (apply map vector)))

(defn get-entitlements-for-csv-export [{:keys [user-id resource-ext-id expired separator]}]
  (let [entitlements (entitlements/get-entitlements {:user user-id
                                                     :resource-ext-id resource-ext-id
                                                     :active-at (when-not expired
                                                                  (time/now))})
        [column-names columns] (get-entitlement-csv-format)]
    (print-to-csv {:column-names column-names ; XXX: consider localizing columns
                   :rows (mapv (apply juxt columns) entitlements)
                   :separator separator})))

(defn- add-to-outbox! [action type entitlements config]
  (outbox/put! {:outbox/type :entitlement-post
                :outbox/deadline (time/plus (time/now) (time/days 1)) ;; hardcoded for now
                :outbox/entitlement-post {:action action
                                          :type type
                                          :entitlements entitlements
                                          :config config}}))

(defn grant-entitlements! [application-id user-id resource-ids actor end]
  (log/info "granting entitlements on application" application-id "to" user-id "resources" resource-ids "until" end)
  ;; TODO could generate only one outbox entry per application. Currently one per user-resource pair.
  (entitlements/grant-entitlements! application-id user-id resource-ids actor end)
  (doseq [resource-id (sort resource-ids)]
    (let [entitlements (entitlements/get-entitlements {:application application-id :user user-id :resource resource-id})]
      (add-to-outbox! :add :basic entitlements nil)
      (add-to-outbox! :add :plugin entitlements nil)
      (doseq [config (:entitlement-push env)]
        (add-to-outbox! :add (:type config) entitlements config)))))

(defn revoke-entitlements! [application-id user-id resource-ids actor end]
  (log/info "revoking entitlements on application" application-id "to" user-id "resources" resource-ids "at" end)
  (entitlements/revoke-entitlements! application-id user-id resource-ids actor end)
  (doseq [resource-id (sort resource-ids)]
    (let [entitlements (entitlements/get-entitlements {:application application-id :user user-id :resource resource-id})]
      (add-to-outbox! :remove :basic entitlements nil)
      (doseq [config (:entitlement-push env)]
        (add-to-outbox! :remove (:type config) entitlements config)))))

(defn update-entitlements-for-application
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
        application-entitlements (entitlements/get-entitlements-by-user application-id)
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
