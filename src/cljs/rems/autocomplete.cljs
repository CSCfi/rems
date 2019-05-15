(ns rems.autocomplete
  (:require [komponentit.autocomplete :as autocomplete]
            [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;; TODO: add an ID to the input field and then link all labels to it
(defn component
  "Multiple selectable, searchable list"
  [{:keys [value items add-fn remove-fn item->key item->text item->value value->text search-fields term-match-fn max-results]
    :or {item->key :key item->value :value item->text :value value->text get search-fields [:value] max-results 25}}]
  [autocomplete/multiple-autocomplete
   (merge {:value value
           :on-change add-fn
           :on-remove remove-fn
           :item->key item->key
           :item->text item->text
           :item->value item->value
           :value->text value->text
           :max-results max-results
           :placeholder (text :t.autocomplete/placeholder)
           :no-results-text (text :t.autocomplete/no-results)
           :ctrl-class "autocomplete"
           :items items}
          (if term-match-fn
            {:term-match-fn term-match-fn}
            {:search-fields search-fields}))])

(defn guide
  []
  (let [add-fn (fn [item] (println "add" item))
        remove-fn (fn [item] (println "remove" item))]
    [:div
     (component-info component)
     (example "autocomplete, empty"
              [component {:value nil :items (sorted-map 1 "Alice" 2 "Bob" 3 "Carl" 4 "Own" 5 "Deve")
                          :add-fn add-fn :remove-fn remove-fn}])
     (example "autocomplete, multiple values selected"
              [component {:value #{1 5} :items (sorted-map 1 "Alice" 2 "Bob" 3 "Carl" 4 "Own" 5 "Deve")
                          :add-fn add-fn :remove-fn remove-fn}])]))
