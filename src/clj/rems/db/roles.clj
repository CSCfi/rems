(ns rems.db.roles
  (:require [medley.core :refer [map-vals]]
            [rems.cache :as cache]
            [rems.common.util :refer [getx]]
            [rems.db.core :as db]
            [rems.util :refer [errorf]]))

(def +db-roles+
  "The roles that are set for users in the database instead of setting them dynamically."
  #{:owner :reporter :user-owner :expirer})

(defn- role-from-db [{:keys [role]}]
  (or (+db-roles+ (keyword role))
      (errorf "Unknown role: %s" (pr-str role))))

(defn- role-to-db [role]
  (if (contains? +db-roles+ role)
    (name role)
    (errorf "Unknown role: %s" (pr-str role))))

(defn- parse-roles [xs]
  (into #{} (map role-from-db) xs))

(def role-cache
  (cache/basic {:id ::role-cache
                :miss-fn (fn [userid]
                           (if-let [roles (db/get-roles {:user userid})]
                             (parse-roles roles)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-all-roles)
                                  (group-by :userid)
                                  (map-vals parse-roles)))}))

(def ^:private users-by-role
  (cache/basic {:id ::users-by-role-cache
                :depends-on [::role-cache]
                :reload-fn (fn [deps]
                             (let [role-user-pairs (for [[userid roles] (getx deps ::role-cache)
                                                         role roles]
                                                     {role #{userid}})]
                               (apply merge-with into role-user-pairs)))}))

(defn get-roles [userid]
  (->> (cache/lookup-or-miss! role-cache userid)
       (into #{:logged-in}))) ; base role for everybody

(defn get-users-with-role [role]
  (cache/lookup! users-by-role role #{}))

(defn add-role! [userid role]
  (db/add-role! {:user userid :role (role-to-db role)})
  (cache/miss! role-cache userid))

(defn remove-role! [userid role]
  (db/remove-role! {:user userid :role (role-to-db role)})
  (cache/miss! role-cache userid))

(defn remove-roles! [userid]
  (db/remove-roles! {:user userid})
  (cache/evict! role-cache userid))

(defn update-roles! [userid roles]
  (->> roles
       (run! #(db/add-role! {:user userid
                             :role (role-to-db %)}))))
