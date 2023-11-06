(ns rems.db.entitlements
  "Creating and fetching entitlements."
  (:require [clj-time.core :as time]
            [rems.db.core :as db]))

(defn get-entitlements [params]
  (db/get-entitlements params))

(defn grant-entitlements! [application-id user-id resource-ids actor end]
  (doseq [resource-id (sort resource-ids)]
    (db/add-entitlement! {:application application-id
                          :user user-id
                          :resource resource-id
                          :approvedby actor
                          :start (time/now) ; TODO should ideally come from the command time
                          :end end})))

(defn revoke-entitlements! [application-id user-id resource-ids actor end]
  (doseq [resource-id (sort resource-ids)]
    (db/end-entitlements! {:application application-id
                           :user user-id
                           :resource resource-id
                           :revokedby actor
                           :end end})))

(defn get-entitlements-by-user [application-id]
  (->> (db/get-entitlements {:application application-id :active-at (time/now)})
       (group-by :userid)
       (map (fn [[userid rows]]
              [userid (set (map :resourceid rows))]))
       (into {})))

(defn update-entitlement! [params]
  (db/update-entitlement! params))
