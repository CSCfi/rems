(ns rems.form-validation
  "Pure functions for form validation logic"
  (:require [clojure.string :as str]
            [rems.common.form :as form]
            [rems.common.util :refer [+email-regex+]]))

(defn- all-columns-set? [field]
  (let [valid-row? (fn [cells] (every? #(not (str/blank? (:value %))) cells))]
    (or (= "" (:field/value field)) ; need to tolerate the default value
        (every? valid-row? (:field/value field)))))

(defn- required-error [field]
  (case (:field/type field)
    (:header :label)
    nil

    :table
    (or
     ;; a non-optional table must have at least one row
     (when (and (not (:field/optional field))
                (empty? (:field/value field)))
       {:field-id (:field/id field)
        :type     :t.form.validation/required})
     ;; all tables must have all columns set for all fields
     ;; TODO consider pointing out the column
     (when (not (all-columns-set? field))
       {:field-id (:field/id field)
        :type     :t.form.validation/column-values-missing}))

    ;; default:
    (when (and (not (:field/optional field))
               (str/blank? (:field/value field)))
      {:field-id (:field/id field)
       :type     :t.form.validation/required})))

(defn- too-long-error [field]
  (when-let [limit (:field/max-length field)]
    (when (> (count (:field/value field)) limit)
      {:field-id (:field/id field)
       :type     :t.form.validation/toolong})))

(defn- invalid-email-address-error [field]
  (when (= (:field/type field) :email)
    (when-not (or (str/blank? (:field/value field))
                  (re-matches +email-regex+ (:field/value field)))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-email})))

(defn- option-value-valid? [field]
  (let [allowed-values (set (conj (map :key (:field/options field)) ""))]
    (contains? allowed-values (:field/value field))))

(defn- invalid-option-error [field]
  (when (= (:field/type field) :option)
    (when-not (option-value-valid? field)
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-value})))

(defn- missing-columns-error [field]
  (when (= (:field/type field) :table)
    (let [columns (set (map :key (:field/columns field)))
          row-ok? (fn [row] (= columns (set (map :column row))))
          value (:field/value field)]
      ;; Schema validation guarantees that it's either a s/Str or
      ;; a [[{:column s/Str :value s/Str}]] so we don't need to check
      ;; the shape of the data here. However, the default value
      ;; for :field/value is "", which we do need to tolerate.
      (when (or (and (string? value)
                     (not (str/blank? value)))
                (not (every? row-ok? (:field/value field))))
        ;; TODO more specific error?
        {:field-id (:field/id field)
         :type     :t.form.validation/invalid-value}))))

;; TODO: validate that attachments are actually valid?
(defn- invalid-attachment-error [field]
  (when (= (:field/type field) :attachment)
    (when (not (every? number? (form/parse-attachment-ids (:field/value field))))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-value})))

(defn- wrong-value-type-error [field]
  (when-not (= :table (:field/type field))
    (when (and (not (string? (:field/value field)))
               (not (nil? (:field/value field))))
      ;; TODO more specific error?
      {:field-id (:field/id field)
       :type :t.form.validation/invalid-value})))

(defn- validate-field-content [field]
  (or (wrong-value-type-error field)
      (invalid-email-address-error field)
      (too-long-error field)
      (invalid-option-error field)
      (missing-columns-error field)
      (invalid-attachment-error field)))

(defn- validate-field-submit [field]
  (or (required-error field)
      (validate-field-content field)))

(defn- validate-draft-field [field]
  (validate-field-content field))

(defn validate-fields-for-draft [fields]
  (->> (sort-by :field/id fields)
       (filter :field/visible)
       (map validate-draft-field)
       (remove nil?)
       (seq)))

(defn validate-fields-for-submit [fields]
  (->> (sort-by :field/id fields)
       (filter :field/visible)
       (map validate-field-submit)
       (remove nil?)
       (seq)))
