(ns rems.db.user-settings
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [medley.core :refer [dissoc-in]]
            [mount.core :as mount]
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

(def ^:private coerce-user-settings
  (coerce/coercer! DbUserSettings json/coercion-matcher))

(defn- json->settings [json]
  (when json
    (-> json
        json/parse-string
        coerce-user-settings)))

(def user-settings-cache (atom ::uninitialized))

(defn empty-user-settings-cache! []
  (log/info "Emptying low level user-settings cache")
  (reset! user-settings-cache {:user-settings-by-id-cache {}}))

(defn reset-user-settings-cache! []
  (log/info "Resetting low level user-settings cache")
  (reset! user-settings-cache ::uninitialized))

(defn reload-user-settings-cache! []
  (log/info "Reloading low level user-settings cache")
  (let [user-settingss (db/get-user-settings {})
        new-user-settings-by-id-cache (atom (sorted-map))]
    (doseq [user-settings user-settingss]
      (let [fixed-user-settings (-> user-settings
                                    :settings
                                    json->settings)
            id (:userid user-settings)]
        (log/debug "Loading uncached user-settings:" id)
        (swap! new-user-settings-by-id-cache assoc id fixed-user-settings)))
    (reset! user-settings-cache {:user-settings-by-id-cache @new-user-settings-by-id-cache})
    (log/info "Reloaded low level user-settings cache with" (count user-settingss) "user-settings")))

(mount/defstate low-level-user-settings-cache
  :start (reload-user-settings-cache!)
  :stop (reset-user-settings-cache!))

(defn ensure-cache-is-initialized! []
  (assert (not= ::uninitialized @user-settings-cache)))

(defn get-user-settings [userid]
  (ensure-cache-is-initialized!)
  (merge (default-settings)
         (when userid
           (get-in @user-settings-cache [:user-settings-by-id-cache userid]))))

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
      (swap! user-settings-cache assoc-in [:user-settings-by-id-cache userid] new-user-settings)
      {:success true})
    {:success false}))

(defn delete-user-settings! [userid]
  (db/delete-user-settings! {:user userid})
  (swap! user-settings-cache dissoc-in [:user-settings-by-id-cache userid]))
