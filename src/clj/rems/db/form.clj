(ns rems.db.form
  (:require [clojure.test :refer :all]
            [medley.core :refer [filter-vals map-keys]]
            [rems.api.schema :refer [FieldTemplate]]
            [rems.common.form :refer [assign-field-ids] :as common-form]
            [rems.config :refer [env]]
            [rems.api.services.util :as util]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
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
                      :organization :form/organization
                      :title :form/title
                      :fields :form/fields
                      :enabled :enabled
                      :archived :archived}))))

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

(defn- catalogue-items-for-form [id include-archived]
  (->> (catalogue/get-localized-catalogue-items {:form id :archived include-archived})
       (map #(select-keys % [:id :title :localizations]))))

(defn- workflows-for-form [id]
  ;; TODO optimize?
  (->> (workflow/get-workflows {})
       (filter #(contains? (set (get-in % [:workflow :forms])) {:form/id id}))
       (map #(select-keys % [:id :title]))))

(defn- form-in-use-error [form-id]
  (let [catalogue-items (seq (catalogue-items-for-form form-id true))
        workflows (seq (workflows-for-form form-id))]
    (when (or catalogue-items workflows)
      {:success false
       :errors [{:type :t.administration.errors/form-in-use
                 :catalogue-items catalogue-items
                 :workflows workflows}]})))

(defn form-editable [form-id]
  (or (form-in-use-error form-id)
      {:success true}))

(defn validate-given-ids
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
       (assign-field-ids)
       (normalize-field-definitions)
       (validate-fields)
       (json/generate-string)))

(defn- validation-error [form]
  (when-let [error-map (common-form/validate-form-template form (:languages env))]
    {:success false
     :errors [error-map]}))

(defn create-form! [user-id form]
  (let [organization (:form/organization form)]
    (util/check-allowed-organization! organization)
    (let [form-id (:id (db/save-form-template! {:organization organization
                                                :title (:form/title form)
                                                :user user-id
                                                :fields (serialize-fields form)}))]
      {:success (not (nil? form-id))
       :id form-id})))

(defn edit-form! [user-id form]
  (let [form-id (:form/id form)
        organization (:form/organization form)]
    ;; need to check both previous and new organization
    (util/check-allowed-organization! (:form/organization (get-form-template form-id)))
    (util/check-allowed-organization! organization)
    (or (form-in-use-error form-id)
        (do (db/edit-form-template! {:id form-id
                                     :organization organization
                                     :title (:form/title form)
                                     :user user-id
                                     :fields (serialize-fields form)})
            {:success true}))))

(defn set-form-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:form/organization (get-form-template id)))
  (db/set-form-template-enabled! {:id id :enabled enabled})
  {:success true})

(defn set-form-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:form/organization (get-form-template id)))
  (let [catalogue-items (catalogue-items-for-form id false)]
    (if (and archived (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/form-in-use :catalogue-items catalogue-items}]}
      (do
        (db/set-form-template-archived! {:id id
                                         :archived archived})
        {:success true}))))
