(ns rems.form-validation
  "Pure functions for form validation logic"
  (:require [clojure.string :as str]))

(defn- required? [field]
  (and (not (:field/optional field))
       (not (contains? #{:header :label} (:field/type field)))
       (str/blank? (:field/value field))))

(defn- too-long? [field]
  (and (:field/max-length field)
       (> (count (:field/value field))
          (:field/max-length field))))

(defn- validate-field [field]
  (cond
    (required? field) {:field-id (:field/id field)
                       :type :t.form.validation/required}
    (too-long? field) {:field-id (:field/id field)
                       :type :t.form.validation/toolong}))

(defn validate-fields [fields]
  (->> (sort-by :field/id fields)
       (filter :field/visibility)
       (map validate-field)
       (remove nil?)
       (seq)))
