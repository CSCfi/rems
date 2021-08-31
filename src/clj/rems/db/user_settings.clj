(ns rems.db.user-settings
  (:require [clojure.string :as str]
            [rems.common.util :refer [+email-regex+]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(defn- default-settings []
  {:language (:default-language env)
   :notification-email nil})

;; TODO should this be in schema-base?
(s/defschema UserSettings
  {:language s/Keyword
   :notification-email (s/maybe s/Str)
   (s/optional-key :ega) {(s/optional-key :api-key-expiration-date) DateTime}})

(s/defschema DbUserSettings
  {:language s/Keyword
   (s/optional-key :notification-email) (s/maybe s/Str)
   (s/optional-key :ega) {(s/optional-key :api-key-expiration-date) DateTime}})

(def ^:private validate-user-settings
  (s/validator UserSettings))

(defn- settings->json [settings]
  (-> settings
      validate-user-settings
      json/generate-string))

(def ^:private coerce-user-settings
  (coerce/coercer! DbUserSettings json/coercion-matcher))

(defn- json->settings [json]
  (when json
    (-> json
        json/parse-string
        coerce-user-settings)))

(defn get-user-settings [user]
  (merge (default-settings)
         (when user
           (json->settings (:settings (db/get-user-settings {:user user}))))))

(defn validate-new-settings
  "Validates the new settings.

  Returns `nil` if the settings were invalid, distinct from `{}` if nothing needs to be done."
  [{:keys [language notification-email] :as new-settings}]
  (let [valid-new-settings (merge {} ; valid returns at least a map
                                  (when (contains? (set (:languages env)) language)
                                    {:language language})
                                  (when (and notification-email (re-matches +email-regex+ notification-email))
                                    {:notification-email notification-email})
                                  (when (and (contains? new-settings :notification-email)
                                             (str/blank? notification-email))
                                    ;; clear notification email to use identity provider's email instead
                                    {:notification-email nil})
                                  (when (:enable-ega env)
                                    (when-let [ega (:ega new-settings)]
                                      {:ega (select-keys ega [:api-key-expiration-date])})))]
    (when (= (set (keys valid-new-settings)) ; fail completely if any were invalid
             (set (keys new-settings)))
      valid-new-settings)))

(defn update-user-settings! [user new-settings]
  (assert user "User missing!")
  (if-let [validated (validate-new-settings new-settings)]
    (do
      (db/update-user-settings! {:user user
                                 :settings (settings->json
                                            (merge (get-user-settings user)
                                                   validated))})
      {:success true})
    {:success false}))

(defn delete-user-settings! [user]
  (db/delete-user-settings! {:user user}))
