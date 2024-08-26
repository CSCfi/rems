(ns rems.db.form
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set]
            [medley.core :refer [filter-vals]]
            [rems.api.schema :as schema]
            [rems.cache :as cache]
            [rems.common.form :as common-form]
            [rems.common.util :refer [apply-filters getx index-by]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import rems.InvalidRequestException))

(def ^:private coerce-fields
  (coerce/coercer! [schema/FieldTemplate] coerce/string-coercion-matcher))

(s/defschema FormData
  {:form/internal-name s/Str
   :form/external-title schema-base/LocalizedString})

(def ^:private coerce-formdata
  (coerce/coercer! FormData coerce/string-coercion-matcher))

(defn- parse-form-template-raw [x]
  (let [fields (-> (:fields x) json/parse-string coerce-fields)
        data (-> (:formdata x) json/parse-string coerce-formdata)
        form {:form/id (:id x)
              :form/internal-name (:form/internal-name data)
              :form/external-title (:form/external-title data)
              :form/fields fields
              :form/title (:form/internal-name data) ; XXX: add deprecated title - could this be removed?
              :archived (:archived x)
              :enabled (:enabled x)
              :organization {:organization/id (:organization x)}}]
    form))

(def form-template-cache
  (cache/basic {:id ::form-template-cache
                :miss-fn (fn [id]
                           (if-let [form (db/get-form-template {:id id})]
                             (parse-form-template-raw form)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-form-templates)
                                  (map parse-form-template-raw)
                                  (index-by [:form/id])))}))

(defn get-form-template [id]
  (cache/lookup-or-miss! form-template-cache id))

(defn get-form-templates [& [filters]]
  (->> (vals (cache/entries! form-template-cache))
       (into [] (apply-filters filters))))

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

(def ^:private validate-fields
  (s/validator [schema/FieldTemplate]))

(defn- serialize-fields [form]
  (->> (:form/fields form)
       (validate-given-ids)
       (common-form/assign-field-ids)
       (mapv normalize-field-definition)
       (validate-fields)
       (json/generate-string)))

(def ^:private validate-formdata
  (s/validator FormData))

(defn- serialize-formdata [formdata]
  (-> formdata
      validate-formdata
      json/generate-string))

(defn save-form-template! [form]
  (let [id (:id (db/save-form-template! {:organization (:organization/id (:organization form))
                                         :formdata (serialize-formdata {:form/internal-name (:form/internal-name form)
                                                                        :form/external-title (:form/external-title form)})
                                         :fields (serialize-fields form)}))]
    (cache/miss! form-template-cache id)
    id))

(defn edit-form-template! [form]
  (let [id (:form/id form)]
    (assert id)
    (db/edit-form-template! {:id id
                             :organization (:organization/id (:organization form))
                             :formdata (serialize-formdata {:form/internal-name (or (:form/internal-name form) (:form/title form))
                                                            :form/external-title (:form/external-title form)})
                             :fields (serialize-fields form)})
    (cache/miss! form-template-cache id)
    id))

(comment
  (defn update-form-template!
    "Updates a form template. Not user action like #'edit-form-template! or #'save-form-template! are."
    [form]
    (let [id (:id (db/update-form-template! {:id (getx form :form/id)
                                             :organization (:organization/id (:organization form))
                                             :formdata (serialize-formdata {:form/internal-name (:form/internal-name form)
                                                                            :form/external-title (:form/external-title form)})
                                             :fields (serialize-fields form)}))]
      (cache/miss! form-template-cache id)
      id)))

(defn set-enabled! [id enabled?]
  (assert id)
  (db/set-form-template-enabled! {:id id :enabled enabled?})
  (cache/miss! form-template-cache id))

(defn set-archived! [id archived?]
  (assert id)
  (db/set-form-template-archived! {:id id :archived archived?})
  (cache/miss! form-template-cache id))
