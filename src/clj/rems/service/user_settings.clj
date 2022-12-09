(ns rems.service.user-settings
  (:require [rems.db.user-settings :as user-settings]))

(def UserSettings user-settings/UserSettings)

(defn get-user-settings [userid]
  (user-settings/get-user-settings userid))

(defn update-user-settings! [userid settings]
  (user-settings/update-user-settings! userid settings))
