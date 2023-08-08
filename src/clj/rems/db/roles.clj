(ns rems.db.roles
  (:require [rems.db.core :as db]
            [rems.util :refer [errorf]]))

;; The roles that are set for users in the database instead of setting them dynamically.
(def ^:private +db-roles+ #{:owner :reporter :user-owner :expirer})

(defn- role-from-db [{role-string :role}]
  (let [role (keyword role-string)]
    (when-not (+db-roles+ role)
      (errorf "Unknown role: %s" (pr-str role-string)))
    role))

(defn- role-to-db [role]
  (when-not (+db-roles+ role)
    (errorf "Unknown role: %s" (pr-str role)))
  (name role))

(defn get-all-roles []
  (for [[userid roles] (group-by :userid (db/get-all-roles nil))
        :let [roles (-> (map role-from-db roles)
                        set
                        (conj :logged-in))]] ; base role for everybody
    {:userid userid
     :roles roles}))

(defn get-roles [useruser]
  (let [roles (set (map role-from-db (db/get-roles {:user useruser})))]
    (conj roles :logged-in))) ; base role for everybody

(defn add-role! [userid role]
  (db/add-role! {:user userid :role (role-to-db role)})
  nil)

(defn remove-role! [userid role]
  (db/remove-role! {:user userid :role (role-to-db role)})
  nil)

(defn remove-roles! [userid]
  (db/remove-roles! {:user userid})
  nil)

(defn update-roles! [{:keys [userid roles]}]
  (assert userid)
  (doseq [role roles
          :when (not= :logged-in role)] ; base role for everybody
    (db/add-role! {:user userid
                   :role (role-to-db role)})))

(comment
  (get-roles "owner")
  (get-roles "frank")
  (db/get-roles {:user "owner"})
  (db/get-roles {:user "frank"})
  (db/remove-roles! {:user "frank"}))
