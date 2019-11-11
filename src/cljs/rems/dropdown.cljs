(ns rems.dropdown
  (:require [cljsjs.react-select]
            [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn dropdown
  "Single- or multi-choice, searchable dropdown menu."
  [{:keys [id items item-key item-label item-selected? item-disabled? multi? clearable? on-change]
    :or {item-selected? (constantly false)
         item-disabled? (constantly false)}}]
  (let [options (map (fn [item] {:value item
                                 :label (item-label item)
                                 :isDisabled (item-disabled? item)})
                     items)
        grouped (group-by #(item-selected? (% :value)) options)]
    [:> js/Select {:className "dropdown-container"
                   :classNamePrefix "dropdown-select"
                   :getOptionValue #(let [item (:value (js->clj % :keywordize-keys true))]
                                      (item-key item))
                   :inputId id
                   :isMulti multi?
                   :isClearable clearable?
                   :maxMenuHeight 200
                   :noOptionsMessage #(text :t.dropdown/no-results)
                   :options (if multi?
                              (get grouped false [])
                              options)
                   :onChange #(let [new-value (js->clj %1 :keywordize-keys true)]
                                (on-change (if multi?
                                             (map :value new-value)
                                             (:value new-value))))
                   :placeholder (text :t.dropdown/placeholder)
                   :value (get grouped true [])}]))

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
                         :item-key :id
                         :item-label :userid
                         :on-change on-change}])
     (example "dropdown menu, single-choice, selected item Bob"
              [dropdown {:items items
                         :item-key :id
                         :item-label :userid
                         :item-selected? #(= "Bob" (:userid %))
                         :on-change on-change}])
     (example "dropdown menu, multi-choice, several values selected"
              [dropdown {:items items
                         :item-key :id
                         :item-label :userid
                         :item-selected? #(contains? #{1 3 5} (% :id))
                         :multi? true
                         :on-change on-change}])]))
