(ns rems.form-validation
  "Pure functions for form validation logic")

(defn- validate-field [field]
  (if (empty? (:value field))
    (when-not (or (:optional field) (= "label" (:type field)))
      {:field-id (:id field)
       :type :t.form.validation/required})
    (when (and (:maxlength field)
               (> (count (:value field)) (:maxlength field)))
      {:field-id (:id field)
       :type :t.form.validation/toolong})))

(defn validate-fields [fields]
  (->> (sort-by :id fields)
       (map validate-field)
       (remove nil?)
       (seq)))
