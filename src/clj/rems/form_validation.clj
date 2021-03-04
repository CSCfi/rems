(ns rems.form-validation
  "Pure functions for form validation logic"
  (:require [clojure.string :as str]
            [rems.common.form :as form]
            [rems.common.util :refer [+email-regex+]]))

(defn- all-columns-set? [field]
  (let [valid-row? #(not-any? str/blank? (map :value %))]
    (every? valid-row? (:field/value field))))

(defn- required-error [field]
  (when (:field/visible field)
    (case (:field/type field)
      (:header :label)
      nil

      :table
      (or
       ;; a non-optional table must have at least one row
       (when-not (:field/optional field)
         (when (empty? (:field/value field))
           {:field-id (:field/id field)
            :type     :t.form.validation/required}))
       ;; all tables must have all columns set for all rows
       (when-not (all-columns-set? field)
         {:field-id (:field/id field)
          :type     :t.form.validation/column-values-missing}))

      ;; default:
      (when-not (:field/optional field)
        (when (str/blank? (:field/value field))
          {:field-id (:field/id field)
           :type     :t.form.validation/required})))))

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
      ;; a [[{:column s/Str :value s/Str}]], and we've ruled out s/Str
      ;; in wrong-value-type-error
      (when-not (every? row-ok? value)
        ;; TODO more specific error?
        {:field-id (:field/id field)
         :type     :t.form.validation/invalid-value}))))

;; TODO: validate that attachments are actually valid?
(defn- invalid-attachment-error [field]
  (when (= (:field/type field) :attachment)
    (when-not (every? number? (form/parse-attachment-ids (:field/value field)))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-value})))

(defn- wrong-value-type-error [field]
  (let [value (:field/value field)]
    (case (:field/type field)
      :table
      (when-not (sequential? value)
        {:field-id (:field/id field)
         :type :t.form.validation/invalid-value})

      ;; default
      (when-not (or (nil? (:field/value field))
                    (string? (:field/value field)))
        {:field-id (:field/id field)
         :type :t.form.validation/invalid-value}))))

(defn- invisible-field-error [field]
  (when-not (:field/visible field)
    (when-not (empty? (:field/value field))
      {:field-id (:field/id field)
       :type :t.form.validation/invisible-field})))

(defn- validate-field-content [field]
  (or (invisible-field-error field)
      (wrong-value-type-error field)
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
       (map validate-draft-field)
       (remove nil?)
       (seq)))

(defn validate-fields-for-submit [fields]
  (->> (sort-by :field/id fields)
       (map validate-field-submit)
       (remove nil?)
       (seq)))
