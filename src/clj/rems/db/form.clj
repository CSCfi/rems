(ns rems.db.form
  (:require [clojure.test :refer :all]
            [medley.core :refer [map-keys]]
            [rems.api.schema :refer [FieldTemplate]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(def ^:private fields-coercer
  (coerce/coercer [FieldTemplate] coerce/string-coercion-matcher))

(defn- coerce-fields [fields]
  (let [result (fields-coercer fields)]
    (if (schema.utils/error? result)
      (throw (ex-info "Failed to coerce fields" {:fields fields :error result}))
      result)))

(defn- deserialize-fields [fields-json]
  (coerce-fields (json/parse-string fields-json)))

(defn- parse-db-row [row]
  (-> row
      (update :fields deserialize-fields)
      db/assoc-expired
      (->> (map-keys {:id :form/id
                      :organization :form/organization
                      :title :form/title
                      :fields :form/fields
                      :start :start
                      :end :end
                      :expired :expired
                      :enabled :enabled
                      :archived :archived}))))

(defn get-form-templates [filters]
  (->> (db/get-form-templates)
       (map parse-db-row)
       (db/apply-filters filters)))

(defn get-form-template [id]
  (let [row (db/get-form-template {:id id})]
    (when row
      (parse-db-row row))))

(defn- catalogue-items-for-form [id]
  (->> (catalogue/get-localized-catalogue-items {:form id :archived false})
       (map #(select-keys % [:id :title :localizations]))))

(defn- form-in-use-error [form-id]
  (let [catalogue-items (catalogue-items-for-form form-id)]
    (when (seq catalogue-items)
      {:success false
       :errors [{:type :t.administration.errors/form-in-use :catalogue-items catalogue-items}]})))

(defn form-editable [form-id]
  (or (form-in-use-error form-id)
      {:success true}))

(defn- generate-field-ids [fields]
  (map-indexed (fn [index field]
                 (assoc field :field/id (inc index)))
               fields))

(defn- serialize-fields [form]
  (->> (:form/fields form)
       (generate-field-ids)
       (s/validate [FieldTemplate])
       (json/generate-string)))

(defn create-form! [user-id form]
  (let [form-id (:id (db/save-form-template! {:organization (:form/organization form)
                                              :title (:form/title form)
                                              :user user-id
                                              :fields (serialize-fields form)}))]
    {:success (not (nil? form-id))
     :id form-id}))

(defn edit-form! [user-id form]
  (let [form-id (:form/id form)]
    (or (form-in-use-error form-id)
        (do (db/edit-form-template! {:id form-id
                                     :organization (:form/organization form)
                                     :title (:form/title form)
                                     :user user-id
                                     :fields (serialize-fields form)})
            {:success true}))))

(defn set-form-enabled! [command]
  (db/set-form-template-enabled! (select-keys command [:id :enabled]))
  {:success true})

(defn set-form-archived! [{:keys [id archived]}]
  (let [catalogue-items (catalogue-items-for-form id)]
    (if (and archived (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/form-in-use :catalogue-items catalogue-items}]}
      (do
        (db/set-form-template-archived! {:id id
                                         :archived archived})
        {:success true}))))
