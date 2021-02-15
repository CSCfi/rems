(ns rems.form-validation
  "Pure functions for form validation logic"
  (:require [clojure.string :as str]
            [rems.common.form :as form]
            [rems.common.util :refer [+email-regex+]]))

(defn- all-columns-set? [field]
  (let [valid-row? (fn [cells] (every? #(not (str/blank? (:value %))) cells))]
    (or (= "" (:field/value field)) ; need to tolerate the default value
        (every? valid-row? (:field/value field)))))

(defn- required? [field]
  (case (:field/type field)
    (:header :label)
    false

    :table
    (or
     ;; a non-optional table must have at least one row
     (and (not (:field/optional field))
          (empty? (:field/value field)))
     ;; all tables must have all columns set for all fields
     ;; TODO we should generate a different error and not :t.form.validation/required
     (not (all-columns-set? field)))

    ;; default:
    (and (not (:field/optional field))
         (str/blank? (:field/value field)))))

(defn- too-long? [field]
  (and (:field/max-length field)
       (> (count (:field/value field))
          (:field/max-length field))))

(defn- invalid-email-address? [field]
  (and (= (:field/type field) :email)
       (not (str/blank? (:field/value field)))
       (not (re-matches +email-regex+ (:field/value field)))))

(defn- option-value-valid? [field]
  (let [allowed-values (set (conj (map :key (:field/options field)) ""))]
    (contains? allowed-values (:field/value field))))

(defn- invalid-option-value? [field]
  (and (= (:field/type field) :option)
       (not (option-value-valid? field))))

(defn- invalid-column-value? [field]
  (when (= (:field/type field) :table)
    (let [columns (set (map :key (:field/columns field)))
          row-ok? (fn [row] (= columns (set (map :column row))))
          value (:field/value field)]
      ;; Schema validation guarantees that it's either a s/Str or
      ;; a [[{:column s/Str :value s/Str}]] so we don't need to check
      ;; the shape of the data here. However, the default value
      ;; for :field/value is "", which we do need to tolerate.
      (if (string? value)
        (not (str/blank? value))
        (not (every? row-ok? (:field/value field)))))))

;; TODO: validate that attachments are actually valid?
(defn- invalid-attachment-value? [field]
  (and (= (:field/type field) :attachment)
       (not (every? number? (form/parse-attachment-ids (:field/value field))))))

(defn- string-required? [field]
  (and (not= :table (:field/type field))
       (not (string? (:field/value field)))
       (not (nil? (:field/value field)))))

(defn- validate-field-content [field]
  (cond
    (string-required? field) {:field-id (:field/id field)
                              :type :t.form.validation/invalid-value} ; TODO better error?
    (invalid-email-address? field) {:field-id (:field/id field)
                                    :type     :t.form.validation/invalid-email}
    (too-long? field) {:field-id (:field/id field)
                       :type     :t.form.validation/toolong}
    (invalid-option-value? field) {:field-id (:field/id field)
                                   :type     :t.form.validation/invalid-value}
    (invalid-column-value? field) {:field-id (:field/id field)
                                   :type     :t.form.validation/invalid-value} ; TODO better error?
    (invalid-attachment-value? field) {:field-id (:field/id field)
                                       :type     :t.form.validation/invalid-value}))

(defn- validate-field-submit [field]
  (if (required? field)
    {:field-id (:field/id field)
     :type     :t.form.validation/required}
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
