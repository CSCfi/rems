(ns rems.migrations.add-organization-owner-role
  (:require [hugsql.core :as hugsql]
            [rems.json :as json]))

;; This migration goes through all api keys defined in api_key table and
;; adds :organization-owner to all lists of permitted roles that already
;; include :owner.

;; SQL query for upsert-api-key! repeated here so that this migration is standalone.

(hugsql/def-db-fns-from-string
  "
-- :name get-api-keys :?
SELECT apikey, comment, permittedroles::TEXT FROM api_key;

-- :name upsert-api-key! :insert
INSERT INTO api_key (apiKey, comment, permittedRoles)
VALUES (
:apikey,
:comment,
:permittedroles::jsonb
)
ON CONFLICT (apiKey)
DO UPDATE
SET (apiKey, comment, permittedRoles) = (:apikey, :comment, :permittedroles::jsonb);
")

(defn migrate-up [{:keys [conn]}]
  (doseq [{:keys [apikey comment permittedroles]}
          (get-api-keys conn)]
    (let [permitted-roles (set (mapv keyword (json/parse-string permittedroles)))]
      (when (contains? permitted-roles :owner)
        (upsert-api-key! conn {:apikey apikey
                               :comment comment
                               :permittedroles (json/generate-string (conj permitted-roles :organization-owner))})))))
