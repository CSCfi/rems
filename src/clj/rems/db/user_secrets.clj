(ns rems.db.user-secrets
  (:require [rems.common.util :refer [deep-merge]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(s/defschema UserSecrets
  {(s/optional-key :ega) {(s/optional-key :api-key) s/Str}})

(def ^:private validate-user-secrets
  (s/validator UserSecrets))

(defn- secrets->json [secrets]
  (-> secrets
      validate-user-secrets
      json/generate-string))

(def ^:private coerce-user-secrets
  (coerce/coercer! UserSecrets json/coercion-matcher))

(defn- json->secrets [json]
  (when json
    (-> json
        json/parse-string
        coerce-user-secrets)))

(defn get-user-secrets [user]
  (when user
    (json->secrets (:secrets (db/get-user-secrets {:user user})))))

(defn update-user-secrets! [user new-secrets]
  (assert user "User missing!")
  (let [old-secrets (get-user-secrets user)
        validated (try (validate-user-secrets (deep-merge old-secrets new-secrets))
                       (catch Exception _e nil))]
    (if validated
      (do
        (db/update-user-secrets!
         {:user user
          :secrets (secrets->json (merge old-secrets validated))})
        {:success true})
      {:success false})))

(defn delete-user-secrets! [user]
  (db/delete-user-secrets! {:user user}))
