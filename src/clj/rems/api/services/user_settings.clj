(ns rems.api.services.user-settings
  (:require [medley.core :refer [find-first]]
            [rems.config :refer [env]]
            [rems.db.user-settings :as user-settings]
            [rems.ega :as ega]))

(def UserSettings user-settings/UserSettings)

(defn get-user-settings [userid]
  (user-settings/get-user-settings userid))

(defn update-user-settings! [userid settings]
  (user-settings/update-user-settings! userid settings))

(defn generate-ega-api-key! [userid access-token]
  (assert userid "User missing!")
  (let [config (find-first (comp #{:ega} :type) (:entitlement-push env))] ; XXX: limitation of one type EGA configuration
    (assert (seq config) "EGA entitlement push must be configured!")

    (ega/generate-api-key {:userid userid
                           :access-token access-token
                           :config config})))

(defn delete-ega-api-key! [userid access-token]
  (assert userid "User missing!")
  (let [config (find-first (comp #{:ega} :type) (:entitlement-push env))] ; XXX: limitation of one type EGA configuration
    (assert (seq config) "EGA entitlement push must be configured!")

    (ega/delete-api-key {:userid userid
                         :access-token access-token
                         :config config})))
