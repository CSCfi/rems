(ns rems.roles
  (:require [clojure.set :as set]))

(defn- has-some? [expected-roles actual-roles]
  (not (empty? (set/intersection expected-roles actual-roles))))

(defn is-applicant? [roles]
  (has-some? #{:applicant} roles))

(defn is-handler? [roles]
  (has-some? #{:approver ; TODO: remove legacy role
               :reviewer ; TODO: remove legacy role
               :handler
               :commenter
               :past-commenter
               :decider
               :past-decider}
             roles))

(defn is-admin? [roles]
  (has-some? #{:owner} roles))
