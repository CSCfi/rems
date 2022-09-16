(ns rems.form-validation
  "Pure functions for form validation logic"
  (:require [clojure.string :as str]
            [rems.common.form :as form]
            [rems.common.util :refer [+email-regex+
                                      +phone-number-regex+
                                      +ipv4-regex+
                                      +ipv6-regex+
                                      +reserved-ipv4-range-regex+
                                      +reserved-ipv6-range-regex+]]))

(defn- required-error [field]
  (let [field-value (:field/value field)]
    (when (and (:field/visible field)
               (not (:field/optional field)))
      (case (:field/type field)
        (:header :label) nil
        :table ;; a non-optional table must have at least one row
        (when (empty? field-value)
          {:field-id (:field/id field)
           :type :t.form.validation/required})
        ;; default:
        (when (cond
                (string? field-value) (str/blank? field-value)
                :else (nil? field-value))
          {:field-id (:field/id field)
           :type :t.form.validation/required})))))

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

(defn- invalid-phone-number-error [field]
  (when (= (:field/type field) :phone-number)
    (when-not (or (str/blank? (:field/value field))
                  (re-matches +phone-number-regex+ (:field/value field)))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-phone-number})))

(defn- invalid-ip-address-error [field]
  (when (and (= (:field/type field) :ip-address)
             (not (str/blank? (:field/value field))))
    (let [matches #(first (re-matches % (:field/value field)))
          invalid-ip? (not-any? matches [+ipv4-regex+ +ipv6-regex+])
          private-ip? (or (every? matches [+ipv4-regex+ +reserved-ipv4-range-regex+])
                          (every? matches [+ipv6-regex+ +reserved-ipv6-range-regex+]))]
      (or (when invalid-ip? {:field-id (:field/id field)
                             :type :t.form.validation/invalid-ip-address})
          (when private-ip? {:field-id (:field/id field)
                             :type :t.form.validation/invalid-ip-address-private})))))

(defn- option-value-valid? [field]
  (let [allowed-values (set (conj (map :key (:field/options field)) ""))]
    (contains? allowed-values (:field/value field))))

(defn- invalid-option-error [field]
  (when (= (:field/type field) :option)
    (when-not (option-value-valid? field)
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-value})))

(defn- multiselect-value-valid? [field]
  (let [allowed? (set (conj (map :key (:field/options field)) ""))]
    (every? allowed? (form/parse-multiselect-values (:field/value field)))))

(defn- invalid-multiselect-error [field]
  (when (= (:field/type field) :multiselect)
    (when-not (multiselect-value-valid? field)
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-value})))

(defn- missing-columns-error [field]
  (when (= (:field/type field) :table)
    (let [columns (set (map :key (:field/columns field)))
          row-ok? (fn [row] (= columns (set (map :column row))))
          columns-set? (fn [row] (not-any? (comp str/blank? :value) row))
          value (:field/value field)]
      ;; Schema validation guarantees that it's either a s/Str or
      ;; a [[{:column s/Str :value s/Str}]], and we've ruled out s/Str
      ;; in wrong-value-type-error
      (or (when-not (every? row-ok? value)
            {:field-id (:field/id field)
             :type :t.form.validation/invalid-value})
          (when-not (every? columns-set? value)
            {:field-id (:field/id field)
             :type :t.form.validation/column-values-missing})))))

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

(defn- validate-field-content [field]
  (or (wrong-value-type-error field)
      (invalid-email-address-error field)
      (invalid-phone-number-error field)
      (invalid-ip-address-error field)
      (too-long-error field)
      (invalid-option-error field)
      (invalid-multiselect-error field)
      (missing-columns-error field)
      (invalid-attachment-error field)))

(defn- validate-field [field]
  (or (required-error field)
      (validate-field-content field)))

(defn validate-fields [fields]
  (->> (sort-by :field/id fields)
       (keep validate-field)
       not-empty))

