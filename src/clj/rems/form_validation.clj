(ns rems.form-validation
  "Pure functions for form validation logic")

(defn- validate-item
  [item]
  (if (empty? (:value item))
    (when-not (:optional item)
      {:type :item
       :id (:id item)
       :key :t.form.validation/required})
    (when (and (:maxlength item)
               (> (count (:value item)) (:maxlength item)))
      {:type :item
       :id (:id item)
       :key :t.form.validation/toolong})))

(defn- validate-license
  [license]
  (when-not (:approved license)
    {:type :license
     :id (:id license)
     :key :t.form.validation/required}))

(defn validate
  "Validates a filled in form from (get-form-for application).

   Returns either :valid or a sequence of validation errors."
  [form]
  (let [messages (vec (concat (filterv identity (mapv validate-item (sort-by :id (:items form))))
                              (filterv identity (mapv validate-license (sort-by :id (:licenses form))))))]
    (if (empty? messages)
      :valid
      messages)))
