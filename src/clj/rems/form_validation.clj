(ns rems.form-validation
  (:require [rems.text :refer [text-format]]))

(defn- title-localizations [item]
  (into {} (for [[lang {title :title}] (:localizations item)
                 :when title]
             [lang title])))

;; TODO: in the validation :text, we always use the english title for
;; items since they don't have a non-localized title like licenses.
;; Should probably get rid of non-localize title for licenses as well?

(defn- validate-item
  [item]
  (when-not (:optional item)
    (when (empty? (:value item))
      {:type :item
       :id (:id item)
       :title (title-localizations item)
       :key :t.form.validation/required
       :text (text-format :t.form.validation/required (get-in item [:localizations :en :title]))})))

(defn- validate-license
  [license]
  (when-not (:approved license)
    {:type :license
     :id (:id license)
     :title (title-localizations license)
     :key :t.form.validation/required
     :text (text-format :t.form.validation/required (:title license))}))

(defn validate
  "Validates a filled in form from (get-form-for application).

   Returns either :valid or a sequence of validation errors."
  [form]
  (let [messages (vec (concat (filterv identity (mapv validate-item (sort-by :id (:items form))))
                              (filterv identity (mapv validate-license (sort-by :id (:licenses form))))))]
    (if (empty? messages)
      :valid
      messages)))
