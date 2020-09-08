(ns rems.db.user-settings
  (:require [clojure.string :as str]
            [medley.core :refer [map-keys]]
            [rems.common.util :refer [+email-regex+]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(defn- default-settings []
  {:language (:default-language env)
   :notification-email nil})

(s/defschema UserSettings
  {:language s/Keyword
   :notification-email (s/maybe s/Str)})

(def ^:private validate-user-settings
  (s/validator UserSettings))

(defn- settings->json [settings]
  (-> settings
      validate-user-settings
      json/generate-string))

(s/defschema PartialUserSettings
  (map-keys s/optional-key UserSettings))

(def ^:private coerce-partial-user-settings
  (coerce/coercer! PartialUserSettings json/coercion-matcher))

(defn- json->settings [json]
  ;; Allows missing keys, so we don't need to write migrations
  ;; if we add new keys to user settings. Migrations are needed if
  ;; we remove keys or make their validation stricter.
  (when json
    (-> json
        json/parse-string
        coerce-partial-user-settings)))

(defn get-user-settings [user]
  (merge (default-settings)
         (when user
           (json->settings (:settings (db/get-user-settings {:user user}))))))

(defn validate-new-settings [{:keys [language notification-email] :as settings}]
  (into {} [(when (contains? (set (:languages env)) language)
              {:language language})
            (when (and notification-email (re-matches +email-regex+ notification-email))
              {:notification-email notification-email})
            (when (and (contains? settings :notification-email)
                       (str/blank? notification-email))
              ;; clear notification email to use identity provider's email instead
              {:notification-email nil})]))

(defn update-user-settings! [user new-settings]
  (assert user "User missing!")
  (let [old-settings (get-user-settings user)
        validated (validate-new-settings new-settings)]
    (if (= (set (keys validated))
           (set (keys new-settings)))
      (do
        (db/update-user-settings!
         {:user user
          :settings (settings->json (merge old-settings validated))})
        {:success true})
      {:success false})))
