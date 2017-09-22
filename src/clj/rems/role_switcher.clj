(ns rems.role-switcher
  (:require [compojure.core :refer [POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.context :as context]
            [rems.db.roles :as roles]
            [rems.guide :refer :all]
            [rems.text :refer :all]
            [rems.util :refer [get-user-id]]
            [ring.util.response :refer [redirect]]))

(defn- localize-role
  [role]
  (case role
    :applicant :t.roles.names/applicant
    :reviewer :t.roles.names/reviewer
    :approver :t.roles.names/approver
    :t.roles.names/unknown))

(defn has-roles?
  "Checks that the `context/*active-role*` matches one of the given roles."
  [& roles]
  (contains? (set roles) context/*active-role*))

(defmacro when-roles
  "Executes the body when the active role is one of the given roles."
  [roles & body]
  `(when (has-roles? ~@roles)
     ~@body))

(defmacro when-role
  "Executes the body when the active role is the given role."
  [role & body]
  `(when-roles #{~role} ~@body))
