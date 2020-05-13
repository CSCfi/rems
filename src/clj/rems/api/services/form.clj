(ns rems.api.services.form
  (:require [clojure.test :refer :all]
            [medley.core :refer [filter-vals]]
            [rems.api.services.dependencies :as dependencies]
            [rems.api.services.util :as util]
            [rems.api.services.workflow :as workflow]
            [rems.api.schema :refer [FieldTemplate]]
            [rems.common.form :as common-form]
            [rems.config :refer [env]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.json :as json]
            [schema.core :as s])
  (:import rems.InvalidRequestException))

(defn- form-in-use-error [form-id]
  (when-let [errors (dependencies/in-use-errors :t.administration.errors/form-in-use {:form/id form-id})]
    {:success false
     :errors errors}))

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
       (common-form/assign-field-ids)
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

(def get-form-template form/get-form-template)
(def get-form-templates form/get-form-templates)

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
  (if-let [errors (when archived
                    (dependencies/archive-errors :t.administration.errors/form-in-use {:form/id id}))]
    {:success false
     :errors errors}
    (do
      (db/set-form-template-archived! {:id id
                                       :archived archived})
      {:success true})))
