(ns rems.db.form
  (:require [clojure.test :refer :all]
            [medley.core :refer [map-keys filter-vals remove-keys]]
            [rems.api.schema :as schema]
            [rems.common.form :as common-form]
            [rems.common.util :refer [apply-filters getx]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import rems.InvalidRequestException))

(def ^:private coerce-fields
  (coerce/coercer! [schema/FieldTemplate] coerce/string-coercion-matcher))

(defn- deserialize-fields [fields-json]
  (coerce-fields (json/parse-string fields-json)))

(s/defschema FormData
  {:form/internal-name s/Str
   :form/external-title schema-base/LocalizedString})

(def ^:private coerce-formdata
  (coerce/coercer! FormData coerce/string-coercion-matcher))

(defn- deserialize-formdata [row]
  (merge (dissoc row :formdata)
         (coerce-formdata (json/parse-string (:formdata row)))))

(defn- add-deprecated-title [row]
  (assoc row :form/title (:form/internal-name row)))

(defn- parse-db-row [row]
  (-> row
      (update :fields deserialize-fields)
      deserialize-formdata
      (->> (map-keys {:id :form/id
                      :form/internal-name :form/internal-name
                      :form/external-title :form/external-title
                      :organization :organization
                      :fields :form/fields
                      :enabled :enabled
                      :archived :archived})
           (remove-keys nil?))
      add-deprecated-title
      (update :organization (fn [o] {:organization/id o}))))

(defn- add-validation-errors [template]
  (assoc template :form/errors (common-form/validate-form-template template (:languages env))))

(defn get-form-templates [filters]
  (->> (db/get-form-templates)
       (map parse-db-row)
       (apply-filters filters)
       (map add-validation-errors)))

(defn get-form-template [id]
  (let [row (db/get-form-template {:id id})]
    (when row
      (add-validation-errors (parse-db-row row)))))

(defn- validate-given-ids
  "Check that `:field/id` values are distinct, not empty (or not given)."
  [fields]
  (let [fields-with-given-ids (filter #(contains? % :field/id) fields)
        id-counts (frequencies (map :field/id fields-with-given-ids))
        duplicates (keys (filter-vals #(< 1 %) id-counts))]
    (when (some empty? (map :field/id fields-with-given-ids))
      (throw (InvalidRequestException. "field id must not be empty")))
    (when (seq duplicates)
      (throw (InvalidRequestException. (pr-str duplicates))))
    fields))

(deftest test-validate-given-ids
  (testing "when no fields or ids are given"
    (is (= [] (validate-given-ids [])))
    (is (= [{}] (validate-given-ids [{}])))
    (is (= [{} {}] (validate-given-ids [{} {}])))
    (is (= [{:foo 42} {:bar 42}] (validate-given-ids [{:foo 42} {:bar 42}]))))
  (testing "empty id is not valid"
    (is (thrown? InvalidRequestException
                 (validate-given-ids [{:field/id ""}])))
    (is (thrown? InvalidRequestException
                 (validate-given-ids [{:field/id nil}]))))
  (testing "when distinct ids are given"
    (is (= [{:field/id "abc"}] (validate-given-ids [{:field/id "abc"}])))
    (is (= [{:field/id "abc"}
            {:field/id "xyz" :foo 42}]
           (validate-given-ids [{:field/id "abc"}
                                {:field/id "xyz" :foo 42}]))))
  (testing "when duplicates are given"
    (is (thrown? InvalidRequestException
                 (validate-given-ids [{:field/id "abc"}
                                      {:field/id "xyz"}
                                      {:field/id "abc"}])))))

(defn- normalize-field-definition [field]
  (cond-> field
    (= :public (:field/privacy field))
    (dissoc :field/privacy)

    (= :always (get-in field [:field/visibility :visibility/type]))
    (dissoc :field/visibility)))

(defn- normalize-field-definitions [fields]
  (map normalize-field-definition fields))

(def ^:private validate-fields
  (s/validator [schema/FieldTemplate]))

(defn- serialize-fields [form]
  (->> (:form/fields form)
       (validate-given-ids)
       (common-form/assign-field-ids)
       (normalize-field-definitions)
       (validate-fields)
       (json/generate-string)))

(def ^:private validate-formdata
  (s/validator FormData))

(defn- serialize-formdata [formdata]
  (-> formdata
      validate-formdata
      json/generate-string))

(defn save-form-template! [form]
  (:id (db/save-form-template! {:organization (:organization/id (:organization form))
                                :formdata (serialize-formdata {:form/internal-name (:form/internal-name form)
                                                               :form/external-title (:form/external-title form)})
                                :fields (serialize-fields form)})))

(defn edit-form-template! [form]
  (db/edit-form-template! {:id (:form/id form)
                           :organization (:organization/id (:organization form))
                           :formdata (serialize-formdata {:form/internal-name (or (:form/internal-name form) (:form/title form))
                                                          :form/external-title (:form/external-title form)})
                           :fields (serialize-fields form)}))

(defn update-form-template!
  "Updates a form template. Not user action like #'edit-form-template! or #'save-form-template! are."
  [form]
  (:id (db/update-form-template! {:id (getx form :form/id)
                                  :organization (:organization/id (:organization form))
                                  :formdata (serialize-formdata {:form/internal-name (:form/internal-name form)
                                                                 :form/external-title (:form/external-title form)})
                                  :fields (serialize-fields form)})))
