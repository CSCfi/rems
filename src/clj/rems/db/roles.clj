(ns rems.db.roles
  (:require [rems.db.core :as db]
            [rems.util :refer [errorf]]))

(def +roles+ #{:applicant :reviewer :approver})

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
  (set (conj (map role-from-db (db/get-roles {:user user}))
             :applicant)))

(defn add-role! [user role]
  (db/add-role! {:user user :role (role-to-db role)})
  nil)

(defn get-active-role [user]
  (if-let [role (db/get-active-role {:user user})]
    (role-from-db role)
    :applicant))

(defn set-active-role! [user role]
  (db/set-active-role! {:user user :role (role-to-db role)})
  nil)
