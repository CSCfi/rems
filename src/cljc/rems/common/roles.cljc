(ns rems.common.roles
  (:refer-clojure :exclude [when])
  #?(:cljs (:require [re-frame.core :as rf])))

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

#?(:cljs
   (defn when [roles val]
     (clojure.core/when (some (set roles) (:roles @(rf/subscribe [:identity])))
       val)))
