(ns rems.migrations.convert-field-id-to-string
  (:require [hugsql.core :as hugsql]
            [rems.json :as json]))

;; Converts the field/id from int to string

(hugsql/def-db-fns-from-string
  "
-- :name get-form-templates :? :*
SELECT
  id,
  organization,
  title,
  fields::TEXT,
  enabled,
  archived
FROM form_template;

-- :name set-fields! :!
UPDATE form_template
SET fields = (:fields::jsonb)
WHERE id = :id;
")

(defn- migrate-form-field-ids [conn]
  (doseq [{:keys [id fields]} (get-form-templates conn)]
    (let [fields (json/parse-string fields)
          new-fields (mapv (fn [field]
                             (update field :field/id str))
                           fields)]
      (set-fields! conn {:id id :fields (json/generate-string new-fields)}))))

(defn migrate-up [{:keys [conn]}]
  (migrate-form-field-ids conn))
