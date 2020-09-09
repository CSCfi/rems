(ns rems.migrations.refactor-form-templates
  (:require [hugsql.core :as hugsql]
            [medley.core :refer [map-keys]]
            [rems.json :as json]))

(hugsql/def-db-fns-from-string
  "
-- :name get-form-templates :? :*
SELECT
 id,
 fields::TEXT
FROM form_template;

-- :name update-form-template! :!
UPDATE form_template
SET fields = :fields::jsonb
WHERE id = :id;
")

(defn- migrate-field [field]
  (map-keys {:id :field/id
             :type :field/type
             :title :field/title
             :input-prompt :field/placeholder
             :optional :field/optional
             :options :field/options
             :maxlength :field/max-length}
            field))

(defn migrate-up [{:keys [conn]}]
  (doseq [{:keys [id fields]} (get-form-templates conn)]
    (update-form-template! conn {:id id
                                 :fields (->> fields
                                              json/parse-string
                                              (map migrate-field)
                                              json/generate-string)})))
