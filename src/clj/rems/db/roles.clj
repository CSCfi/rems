(ns rems.db.roles
  (:require [rems.db.core :as db]
            [rems.util :refer [errorf]]))

;; The roles that are set for users in the database instead of setting them dynamically.
(def ^:private +db-roles+ #{:owner :reporter :user-owner})

(defn- role-from-db [{role-string :role}]
  (let [role (keyword role-string)]
    (when-not (+db-roles+ role)
      (errorf "Unknown role: %s" (pr-str role-string)))
    role))

(defn- role-to-db [role]
  (when-not (+db-roles+ role)
    (errorf "Unknown role: %s" (pr-str role)))
  (name role))

(defn get-roles [user]
  (let [roles (set (map role-from-db (db/get-roles {:user user})))]
    (conj roles :logged-in))) ; base role for everybody

(defn add-role! [user role]
  (db/add-role! {:user user :role (role-to-db role)})
  nil)
