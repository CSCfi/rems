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

(defn- validate-license
  [license]
  (when-not (:approved license)
    {:license-id (:id license)
     :type :t.form.validation/required}))

(defn validate-licenses [licenses]
  (->> (sort-by :id licenses)
       (map validate-license)
       (remove nil?)
       (seq)))

(defn validate
  "Validates a filled in form from (see rems.db.form/get-form).

   Returns either :valid or a sequence of validation errors."
  [form]
  (let [messages (vec (concat (validate-fields (:items form))
                              (validate-licenses (:licenses form))))]
    (if (empty? messages)
      :valid
      messages)))
