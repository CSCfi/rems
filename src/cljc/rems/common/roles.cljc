(ns rems.common.roles
  (:refer-clojure :exclude [when])
  #?(:clj (:require [rems.context :as context])
     :cljs (:require [re-frame.core :as rf])))

(defn is-logged-in? [roles]
  (some #{:logged-in} roles))

(defn show-applications? [roles]
  (some #{:applicant :member :reporter} roles))

(defn show-all-applications? [roles]
  (some #{:reporter} roles))

(defn show-reviews? [roles]
  (some #{:handler :reviewer :decider :past-reviewer :past-decider} roles))

(defn show-admin-pages? [roles]
  (some #{:organization-owner :owner :handler :reporter} roles))

(def +admin-write-roles+ #{:organization-owner :owner})

(defn disallow-setting-organization? [roles]
  (not-any? #{:organization-owner :owner} roles))

(defn has-roles?
  "Does the current user have one of the given roles?"
  [& roles]
  #?(:clj (do
            (assert (bound? #'context/*roles*) "context/*roles* is not defined")
            (boolean (some (set roles) context/*roles*)))
     :cljs (some (set roles) (:roles @(rf/subscribe [:identity])))))

(defn when [roles val]
  (clojure.core/when (apply has-roles? roles)
    val))
