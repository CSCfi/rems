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

(defn- invalid-option-value? [field]
  (and (= (:field/type field) :option)
       (if (and (:field/optional field) (empty? (:field/value field)))
         false
         (not (contains? (set (map :key (:field/options field))) (:field/value field))))))

(defn- validate-field [field]
  (cond
    (invalid-email-address? field) {:field-id (:field/id field)
                                    :type     :t.form.validation/invalid-email}
    (required? field) {:field-id (:field/id field)
                       :type     :t.form.validation/required}
    (too-long? field) {:field-id (:field/id field)
                       :type     :t.form.validation/toolong}
    (invalid-option-value? field) {:field-id (:field/id field)
                                   :type     :t.form.validation/invalid-value}))

(defn validate-fields [fields]
  (->> (sort-by :field/id fields)
       (filter :field/visible)
       (map validate-field)
       (remove nil?)
       (seq)))
