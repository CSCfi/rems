(ns rems.service.user-settings
  (:require [rems.db.user-settings]))

(def UserSettings rems.db.user-settings/UserSettings)

(defn get-user-settings [userid]
  (rems.db.user-settings/get-user-settings userid))

(defn update-user-settings! [userid settings]
  (rems.db.user-settings/update-user-settings! userid settings))
