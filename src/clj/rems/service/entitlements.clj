(ns rems.service.entitlements
  (:require [clj-time.core :as time]
            [rems.csv]
            [rems.db.core :as db]
            [rems.db.users]))

(defn- format-entitlement [{:keys [resid catappid userid start end mail]}]
  {:resource resid
   :application-id catappid
   :userid userid
   :start start
   :end end
   :mail mail})

(defn- join-dependencies [entitlement]
  (-> entitlement
      (assoc :user (rems.db.users/join-user entitlement))
      (dissoc :userid)))

(defn get-entitlements-for-api [{:keys [user-id resource-ext-id expired]}]
  (->> (db/get-entitlements {:user user-id
                             :resource-ext-id resource-ext-id
                             :active-at (when-not expired
                                          (time/now))})
       (mapv (comp join-dependencies format-entitlement))))

(defn- get-entitlement-csv-format []
  (->> ["resource"    :resid
        "application" :catappid
        "user"        :userid
        "start"       :start
        ; "end"         :end
        ; "mail"        :mail
        ]
       (partition 2)
       (apply map vector)))

(defn get-entitlements-for-csv-export [{:keys [user-id resource-ext-id expired separator]}]
  (let [entitlements (db/get-entitlements {:user user-id
                                           :resource-ext-id resource-ext-id
                                           :active-at (when-not expired
                                                        (time/now))})
        [column-names columns] (get-entitlement-csv-format)]
    (rems.csv/print-to-csv {:column-names column-names ; XXX: consider localizing columns
                            :rows (mapv (apply juxt columns) entitlements)
                            :separator separator})))

