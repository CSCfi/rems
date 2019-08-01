(ns rems.dropdown
  (:require [cljsjs.react-select]
            [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;; TODO: add an ID to the input field and then link all labels to it
(defn dropdown
  "Single- or multi-choice, searchable dropdown menu."
  [{:keys [items item->label item->selected? item->disabled? multi? on-change]
    :or {item->selected? (constantly false)
         item->disabled? (constantly false)}}]
  (let [options (map (fn [item] {:value item
                                 :label (item->label item)
                                 :isDisabled (item->disabled? item)})
                     items)
        grouped (group-by #(item->selected? (% :value)) options)]
    [:> js/Select {:className "dropdown-container"
                   :classNamePrefix "dropdown-select"
                   :isMulti multi?
                   :maxMenuHeight 200
                   :noOptionsMessage #(text :t.dropdown/no-results)
                   :options (if multi?
                              (or (get grouped false) [])
                              options)
                   :onChange #(let [new-value (js->clj %1 :keywordize-keys true)]
                                (on-change (if multi?
                                             (map :value new-value)
                                             (:value new-value))))
                   :placeholder (text :t.dropdown/placeholder)
                   :value (or (get grouped true) [])}]))

(defn guide
  []
  (let [on-change (fn [items] (println "items" items))
        items [{:id 1 :userid "Alice"}
               {:id 2 :userid "Bob"}
               {:id 3 :userid "Carl"}
               {:id 4 :userid "Own"}
               {:id 5 :userid "Deve"}]]
    [:div
     (component-info dropdown)
     (example "dropdown menu, single-choice, empty"
              [dropdown {:items items
                         :item->label :userid
                         :on-change on-change}])
     (example "dropdown menu, multi-choice, several values selected"
              [dropdown {:items items
                         :item->label :userid
                         :item->selected? #(contains? #{1 3 5} (% :id))
                         :multi? true
                         :on-change on-change}])]))
