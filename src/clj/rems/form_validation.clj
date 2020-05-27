(ns rems.form-validation
  "Pure functions for form validation logic"
  (:require [clojure.string :as str]
            [rems.util :as util]))

(defn- required? [field]
  (and (not (:field/optional field))
       (not (contains? #{:header :label} (:field/type field)))
       (str/blank? (:field/value field))))

(defn- too-long? [field]
  (and (:field/max-length field)
       (> (count (:field/value field))
          (:field/max-length field))))

(defn- invalid-email-address? [field]
  (and (= (:field/type field) :email)
       (not (str/blank? (:field/value field)))
       (not (re-matches util/+email-regex+ (:field/value field)))))

(defn- option-value-valid? [field]
  (let [allowed-values (set (conj (map :key (:field/options field)) ""))]
    (contains? allowed-values (:field/value field))))

(defn- invalid-option-value? [field]
  (and (= (:field/type field) :option)
       (not (option-value-valid? field))))

(defn- validate-field-content [field]
  (cond
    (invalid-email-address? field) {:field-id (:field/id field)
                                    :type     :t.form.validation/invalid-email}
    (too-long? field) {:field-id (:field/id field)
                       :type     :t.form.validation/toolong}
    (invalid-option-value? field) {:field-id (:field/id field)
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
