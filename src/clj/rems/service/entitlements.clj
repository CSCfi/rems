(ns rems.service.entitlements
  (:require [clj-time.core :as time]
            [rems.common.roles :refer [has-roles?]]
            [rems.db.core :as db]
            [rems.db.csv :refer [print-to-csv]]
            [rems.db.users :refer [join-user]]
            [rems.text :refer [localize-time]]
            [rems.util :refer [getx-user-id]]))

(defn- format-entitlement [{:keys [resid catappid start end mail userid]}]
  {:resource resid
   :userid userid
   :application-id catappid
   :start start
   :end end
   :mail mail})

(defn- join-dependencies [entitlement]
  (-> entitlement
      (assoc :user (join-user entitlement))
      (dissoc :userid)))

(defn get-entitlements-for-api [{:keys [user-id resource-ext-id expired]}]
  (let [user (if (has-roles? :handler :owner :organization-owner :reporter)
               user-id
               (getx-user-id))]
    (->> (db/get-entitlements {:user user
                               :resource-ext-id resource-ext-id
                               :active-at (when-not expired
                                            (time/now))})
         (mapv (comp join-dependencies format-entitlement)))))

;; XXX: consider localizing columns
(defn get-entitlements-for-csv-export []
  (let [entitlements (db/get-entitlements {})
        columns (juxt :resid :catappid :userid #(-> % :start localize-time))]
    (print-to-csv :column-names ["resource" "application" "user" "start"]
                  :rows (mapv columns entitlements))))

