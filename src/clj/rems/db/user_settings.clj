(ns rems.db.user-settings
  (:require [rems.config :refer [env]]
            [rems.db.core :as db]
            [clojure.tools.logging :as log]
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

(defn update-user-settings! [user {:keys [language]}]
  (assert user "User missing!")
  (let [settings (get-user-settings user)
        update-language? (and language
                              (contains? (set (env :languages)) language))]
    (db/update-user-settings!
     {:user user
      :settings (json/generate-string
                 (merge settings
                        (when update-language?
                          {:language language})))})
    {:success update-language?}))
