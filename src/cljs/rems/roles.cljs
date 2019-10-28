(ns rems.roles
  (:refer-clojure :exclude [when])
  (:require [re-frame.core :as rf]))

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

(defn show-admin-edit-buttons? [roles]
  (some #{:owner} roles))

(defn when [predicate & body]
  (clojure.core/when (predicate (:roles @(rf/subscribe [:identity])))
    (into [:<>] body)))
