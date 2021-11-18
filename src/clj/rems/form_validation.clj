(ns rems.form-validation
  "Pure functions for form validation logic"
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [rems.common.form :as form]
            [rems.common.util :refer [+email-regex+
                                      +phone-number-regex+
                                      +valid-ip-address-regex+
                                      +valid-ip-address-regex-version-six+
                                      +reserved-ip-address-range-regex+
                                      +reserved-ip-address-range-regex-version-six+]]))

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

(defn- invalid-phone-number-error [field]
  (when (= (:field/type field) :phone-number)
    (when-not (or (str/blank? (:field/value field))
                  (re-matches +phone-number-regex+ (:field/value field)))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-phone-number})))

(defn- invalid-ip-address-error [field]
  (when (and (= (:field/type field) :ip-address)
             (not (str/blank? (:field/value field))))
    (cond
      (or
       (and (first (re-matches +valid-ip-address-regex+ (:field/value field)))
            (first (re-matches +reserved-ip-address-range-regex+ (:field/value field))))
       (and (first (re-matches +valid-ip-address-regex-version-six+ (:field/value field)))
            (first (re-matches +reserved-ip-address-range-regex-version-six+ (:field/value field)))))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-ip-address-private}
      (and
       (not (first (re-matches +valid-ip-address-regex+ (:field/value field))))
       (not (first (re-matches +valid-ip-address-regex-version-six+ (:field/value field)))))
      {:field-id (:field/id field)
       :type     :t.form.validation/invalid-ip-address}
      :else nil)))

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

(defn- date-out-of-bound-error [field]
  (when (= (:field/type field) :date)
    (let [bound-type (get-in field [:field/date-bounds :date-bounds/type])
          dt (:field/value field)
          [valid-dt? error] (case bound-type
                              :past [time/before? :t.actions.errors/date-not-in-past]
                              :future [time/after? :t.actions.errors/date-not-in-past]
                              [(constantly true) nil])]
      (when-not (valid-dt? dt (time/today-at 23 59 59))
         {:errors [{:type error}]}))))

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
      (invalid-attachment-error field)
      (date-out-of-bound-error field)))

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
