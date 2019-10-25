(ns rems.roles)

(defn is-logged-in? [roles]
  (some #{:logged-in} roles))

(defn show-applications? [roles]
  (some #{:applicant :member :reporter} roles))

(defn show-all-applications? [roles]
  (some #{:reporter} roles))

(defn show-reviews? [roles]
  (some #{:handler :commenter :decider :past-commenter :past-decider} roles))

(defn show-admin-pages? [roles]
  (some #{:owner :handler} roles))
