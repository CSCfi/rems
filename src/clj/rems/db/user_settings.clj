(ns rems.db.user-settings
  (:require [clojure.string :as str]
            [medley.core :refer [map-vals]]
            [rems.cache :as cache]
            [rems.common.util :refer [+email-regex+]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(defn- default-settings []
  {:language (:default-language env)
   :notification-email nil})

;; TODO should this be in schema-base?
(s/defschema UserSettings
  {:language s/Keyword
   :notification-email (s/maybe s/Str)})

(s/defschema DbUserSettings
  {:language s/Keyword
   (s/optional-key :notification-email) (s/maybe s/Str)})

(def ^:private validate-user-settings
  (s/validator UserSettings))

(defn- settings->json [settings]
  (-> settings
      validate-user-settings
      json/generate-string))

(def ^:private coerce-DbUserSettings
  (coerce/coercer! DbUserSettings json/coercion-matcher))

(defn- parse-user-settings-raw! [x]
  (when-let [settings (:settings x)]
    (-> settings
        json/parse-string
        coerce-DbUserSettings)))

(def user-settings-cache
  (cache/basic {:id ::user-settings-cache
                :miss-fn (fn [userid]
                           (-> (db/get-user-settings {:user userid})
                               first
                               parse-user-settings-raw!))
                :reload-fn (fn []
                             (->> (db/get-user-settings {})
                                  (group-by :userid)
                                  (map-vals parse-user-settings-raw!)))}))

(defn get-user-settings [userid]
  (merge (default-settings)
         (cache/lookup! user-settings-cache userid)))

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
                                    {:notification-email nil}))]
    (when (= (set (keys valid-new-settings)) ; fail completely if any were invalid
             (set (keys new-settings)))
      valid-new-settings)))

(defn update-user-settings! [userid new-settings]
  (assert userid "User missing!")
  (if-let [validated (validate-new-settings new-settings)]
    (let [new-user-settings (merge (get-user-settings userid)
                                   validated)]
      (db/update-user-settings! {:user userid
                                 :settings (settings->json
                                            new-user-settings)})
      (cache/evict-and-miss! user-settings-cache userid)
      {:success true})
    {:success false}))

(defn delete-user-settings! [userid]
  (db/delete-user-settings! {:user userid})
  (cache/evict! user-settings-cache userid))
