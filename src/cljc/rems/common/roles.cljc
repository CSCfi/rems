(ns rems.common.roles
  (:require #?@(:clj
                [[rems.context :as context]]
                :cljs
                [[re-frame.core :as rf]
                 [reagent.core :as r]
                 [rems.globals]])))

(def +admin-write-roles+ #{:organization-owner :owner})
(def +admin-read-roles+ #{:owner :organization-owner :handler :reporter})
(def +handling-roles+ #{:handler :reviewer :decider :past-reviewer :past-decider})

(defn show-applications? [roles]
  (some #{:applicant :member :reporter} roles))

(defn show-all-applications? [roles]
  (some #{:reporter} roles))

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
     :cljs (some (set roles) @rems.globals/roles)))

#?(:cljs
   (def logged-in? (r/track has-roles? :logged-in)))

#?(:cljs
   (defn show-when [roles & body]
     (clojure.core/when (apply has-roles? roles)
       (into [:<>] body))))
#?(:cljs
   (defn show-when-not [roles & body]
     (clojure.core/when-not (apply has-roles? roles)
       (into [:<>] body))))

#?(:cljs
   (defn can-modify-organization-item? [item]
     (let [owner? (has-roles? :owner)
           org-owner? (->> @(rf/subscribe [:owned-organizations])
                           (some (comp #{(-> item :organization :organization/id)} :organization/id)))]
       (or owner?
           org-owner?))))
