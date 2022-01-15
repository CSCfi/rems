(ns rems.common.roles
  (:refer-clojure :exclude [when])
  #?(:clj (:require [rems.context :as context])
     :cljs (:require [re-frame.core :as rf]
                     [rems.common.util :refer [in?]])))

(def +admin-write-roles+ #{:organization-owner :owner})
(def +admin-read-roles+ #{:owner :organization-owner :handler :reporter})

(defn is-logged-in? [roles]
  (some #{:logged-in} roles))

(defn show-applications? [roles]
  (some #{:applicant :member :reporter} roles))

(defn show-all-applications? [roles]
  (some #{:reporter} roles))

(defn show-reviews? [roles]
  (some #{:handler :reviewer :decider :past-reviewer :past-decider} roles))

(defn show-admin-pages? [roles]
  (some +admin-read-roles+ roles))

(defn disallow-setting-organization? [roles]
  (not-any? #{:organization-owner :owner} roles))

(defn has-roles?
  "Does the current user have one of the given roles?"
  [& roles]
  #?(:clj (do
            (assert (bound? #'context/*roles*) "context/*roles* is not defined")
            (boolean (some (set roles) context/*roles*)))
     :cljs (some (set roles) (:roles @(rf/subscribe [:identity])))))

(defn is-with-organization?
  "Determines if the user is part of an organization based on the passed in ID."
  [organization-id]
  #?(:cljs (let [owned-organizations @(rf/subscribe [:owned-organizations])]
             (->> owned-organizations
                 (map #(get-in % [:organization/id]))
                 (in? organization-id)))))

(defn has-permission?
  "Does the current user have the proper permissions?
   Permission is defined by having the proper role and is within an org."
  [roles organization-id]
  #?(:cljs
     (and (apply has-roles? roles) (is-with-organization? organization-id))))

#?(:cljs
   (defn show-when
     ([roles & body]
      (clojure.core/when (apply has-roles? roles)
                         (into [:<>] body)))))

#?(:cljs
   (defn show-when-correct-access
     [roles organization-id & body]
     (clojure.core/when (has-permission? roles organization-id)
                        (into [:<>] body))))
