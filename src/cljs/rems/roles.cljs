(ns rems.roles)

(defn is-logged-in? [roles]
  (some #{:logged-in} roles))

(defn is-applicant-or-member? [roles]
  (some #{:applicant :member} roles))

(defn is-reviewer? [roles]
  (some #{:handler :commenter :decider :past-commenter :past-decider :reporter} roles))

(defn is-admin? [roles]
  ;; TODO: add admin role (owner is a business user; admin is a technical user)
  (some #{:owner} roles))
