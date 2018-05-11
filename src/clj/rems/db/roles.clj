(ns rems.db.roles
  (:require [rems.db.core :as db]
            [rems.util :refer [errorf]]))

(def +roles+ #{:applicant :reviewer :approver :owner})

(defn- role-from-db [{role-string :role}]
  (let [role (keyword role-string)]
    (when-not (+roles+ role)
      (errorf "Unknown role: %s" (pr-str role-string)))
    role))

(defn- role-to-db [role]
  (when-not (+roles+ role)
    (errorf "Unknown role: %s" (pr-str role)))
  (name role))

(defn get-roles [user]
  (let [roles (set (map role-from-db (db/get-roles {:user user})))]
    (if (empty? roles)
      #{:applicant} ;; default role
      roles)))

(defn add-role! [user role]
  (db/add-role! {:user user :role (role-to-db role)})
  nil)
