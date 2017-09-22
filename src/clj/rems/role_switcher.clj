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
  "Checks that the `context/*roles*` contains one of the given roles."
  [& roles]
  (assert (bound? #'context/*roles*) "context/*roles* is not defined")
  (boolean (some (set roles) context/*roles*)))

(defmacro when-roles
  "Executes the body when one of the given roles is present."
  [roles & body]
  `(when (has-roles? ~@roles)
     ~@body))

(defmacro when-role
  "Executes the body when the given role is present."
  [role & body]
  `(when-roles #{~role} ~@body))
