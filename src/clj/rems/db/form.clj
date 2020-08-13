(ns rems.db.form
  (:require [clojure.test :refer :all]
            [medley.core :refer [map-keys]]
            [rems.api.schema :refer [FieldTemplate]]
            [rems.common.form :as common-form]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]))

(def ^:private coerce-fields
  (coerce/coercer! [FieldTemplate] coerce/string-coercion-matcher))

(defn- deserialize-fields [fields-json]
  (coerce-fields (json/parse-string fields-json)))

(defn- parse-db-row [row]
  (-> row
      (update :fields deserialize-fields)
      (->> (map-keys {:id :form/id
                      :organization :organization
                      :title :form/title
                      :fields :form/fields
                      :enabled :enabled
                      :archived :archived}))
      (update :organization (fn [o] {:organization/id o}))))

(defn- add-validation-errors [template]
  (assoc template :form/errors (common-form/validate-form-template template (:languages env))))

(defn get-form-templates [filters]
  (->> (db/get-form-templates)
       (map parse-db-row)
       (db/apply-filters filters)
       (map add-validation-errors)))

(defn get-form-template [id]
  (let [row (db/get-form-template {:id id})]
    (when row
      (add-validation-errors (parse-db-row row)))))
