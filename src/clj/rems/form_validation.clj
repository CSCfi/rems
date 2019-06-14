(ns rems.form-validation
  "Pure functions for form validation logic")

(defn- validate-field [field]
  (if (empty? (:field/value field))
    (when-not (or (:field/optional field)
                  (= :label (:field/type field)))
      ;; TODO: use field/id in output
      {:field-id (:field/id field)
       :type :t.form.validation/required})
    (when (and (:field/max-length field)
               (> (count (:field/value field)) (:field/max-length field)))
      {:field-id (:field/id field)
       :type :t.form.validation/toolong})))

(defn validate-fields [fields]
  (->> (sort-by :field/id fields)
       (map validate-field)
       (remove nil?)
       (seq)))
