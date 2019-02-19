(ns rems.roles
  (:require [clojure.set :as set]))

(defn- has-some? [expected-roles actual-roles]
  (not (empty? (set/intersection expected-roles actual-roles))))

(defn is-logged-in? [roles]
  (has-some? #{:logged-in} roles))

(defn is-applicant? [roles]
  (has-some? #{:applicant} roles))

;; TODO: Think of a common name for handlers, commenters and deciders. After removing the legacy workflow, "reviewer" would be a free word.
(defn is-handler-or-commenter-or-decider? [roles]
  (has-some? #{:approver ; TODO: remove legacy role (also from database)
               :reviewer ; TODO: remove legacy role (also from database)
               :handler
               :commenter
               :past-commenter
               :decider
               :past-decider}
             roles))

(defn is-admin? [roles]
  ;; TODO: rename owner role to admin role?
  (has-some? #{:owner} roles))
