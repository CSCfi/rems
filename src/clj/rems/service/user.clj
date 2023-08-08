(ns rems.service.user
  (:require [rems.db.users :as users]
            [rems.db.user-mappings :as user-mappings]
            [rems.db.user-settings :as user-settings]
            [rems.service.cache :as cache]))

(defn get-users-with-role [role]
  (cache/get-users-with-role role))

(defn get-user [userid]
  (users/get-user userid))

(defn find-userid [userid]
  (user-mappings/find-userid userid))

(defn add-user! [command]
  (users/add-user! command))

(defn edit-user! [command]
  (users/edit-user! command))

(def UserSettings user-settings/UserSettings)

(defn get-user-settings [userid]
  (user-settings/get-user-settings userid))

(defn update-user-settings! [userid settings]
  (user-settings/update-user-settings! userid settings))

(defn get-user-settings [userid]
  (user-settings/get-user-settings userid))

(defn get-users []
  (users/get-users))

(defn get-applicants []
  (users/get-applicants))

(defn get-reviewers []
  (users/get-reviewers))

(defn get-deciders []
  (users/get-deciders))

(defn user-exists? [userid]
  (users/user-exists? userid))
