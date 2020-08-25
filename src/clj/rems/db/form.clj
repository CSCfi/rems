(ns rems.db.form
  (:require [clojure.test :refer :all]
            [medley.core :refer [map-keys filter-vals]]
            [rems.api.schema :refer [FieldTemplate]]
            [rems.common.form :as common-form]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import rems.InvalidRequestException))

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
  (s/validator [FieldTemplate]))

(defn- serialize-fields [form]
  (->> (:form/fields form)
       (validate-given-ids)
       (common-form/assign-field-ids)
       (normalize-field-definitions)
       (validate-fields)
       (json/generate-string)))

(defn save-form-template! [user-id form]
  (:id (db/save-form-template! {:organization (:organization/id (:organization form))
                                :title (:form/title form)
                                :user user-id
                                :fields (serialize-fields form)})))

(defn edit-form-template! [user-id form]
  (db/edit-form-template! {:id (:form/id form)
                           :organization (:organization/id (:organization form))
                           :title (:form/title form)
                           :user user-id
                           :fields (serialize-fields form)}))
