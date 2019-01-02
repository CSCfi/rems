(ns rems.form-validation
  "Pure functions for form validation logic")

(defn- validate-item
  [item]
  (if (empty? (:value item))
    (when-not (:optional item)
      {:field-id (:id item)
       :type :t.form.validation/required})
    (when (and (:maxlength item)
               (> (count (:value item)) (:maxlength item)))
      {:field-id (:id item)
       :type :t.form.validation/toolong})))

(defn- validate-license
  [license]
  (when-not (:approved license)
    {:license-id (:id license)
     :type :t.form.validation/required}))

(defn validate
  "Validates a filled in form from (get-form-for application).

   Returns either :valid or a sequence of validation errors."
  [form]
  (let [messages (vec (remove nil? (concat (mapv validate-item (sort-by :id (:items form)))
                                           (mapv validate-license (sort-by :id (:licenses form))))))]
    (if (empty? messages)
      :valid
      messages)))
