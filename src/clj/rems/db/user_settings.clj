(ns rems.db.user-settings
  (:require [rems.db.core :as db]
            [clojure.tools.logging :as log]
            [rems.json :as json]))

(defn update-user-settings! [user settings]
  (assert (and user settings) "User or user settings missing!")
  (db/update-user-settings!
   {:user user
    :settings (json/generate-string {:language (:language settings)})})
  {:success true})

(defn- parse-settings [settings]
  (let [{:keys [language]} (json/parse-string settings)]
    {:language (keyword language)}))

(defn get-user-settings [user]
  (let [settings (:settings (db/get-user-settings {:user user}))]
    (if settings
      (parse-settings settings)
      {})))
