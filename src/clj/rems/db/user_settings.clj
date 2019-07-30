(ns rems.db.user-settings
  (:require [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]))

(defn- default-settings []
  {:language (env :default-language)})

(defn- parse-settings [settings]
  (let [{:keys [language]} (json/parse-string settings)]
    (merge {}
           (when language {:language (keyword language)}))))

(defn get-user-settings [user]
  (merge (default-settings)
         (parse-settings (:settings (db/get-user-settings {:user user})))))

(defn- validate-settings [settings]
  (let [{:keys [language]} settings]
    (when (and language
               (contains? (set (env :languages)) language))
      settings)))

(defn update-user-settings! [user new-settings]
  (assert user "User missing!")
  (let [old-settings (get-user-settings user)
        validated (validate-settings new-settings)]
    (db/update-user-settings!
     {:user user
      :settings (json/generate-string
                 (merge old-settings validated))})
    {:success (some? validated)}))
