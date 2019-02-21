(ns rems.roles)

(defn is-logged-in? [roles]
  (some #{:logged-in} roles))

(defn is-applicant? [roles]
  (some #{:applicant} roles))

;; TODO: Think of a common name for handlers, commenters and deciders. After removing the legacy workflow, "reviewer" would be a free word.
(defn is-handler-or-commenter-or-decider? [roles]
  (some #{:approver ; TODO: remove legacy role (also from database)
          :reviewer ; TODO: remove legacy role (also from database)
          :handler
          :commenter
          :past-commenter
          :decider
          :past-decider}
        roles))

(defn is-admin? [roles]
  ;; TODO: add admin role (owner is a business user; admin is a technical user)
  (some #{:owner} roles))
