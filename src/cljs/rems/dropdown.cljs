(ns rems.dropdown
  (:require [cljsjs.react-select]
            [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn dropdown
  "Single- or multi-choice, searchable dropdown menu."
  [{:keys [id items item-key item-label item-selected? hide-selected? item-disabled? multi? clearable? on-change]
    :or {hide-selected? multi?
         item-selected? (constantly false)
         item-disabled? (constantly false)}}]
  ;; some of the callbacks may be keywords which aren't JS fns so we wrap them in anonymous fns
  [:> js/Select {:className "dropdown-container"
                 :classNamePrefix "dropdown-select"
                 :getOptionLabel #(item-label %)
                 :getOptionValue #(item-key %)
                 :inputId id
                 :isMulti multi?
                 :isClearable clearable?
                 :isOptionDisabled #(item-disabled? %)
                 :maxMenuHeight 200
                 :noOptionsMessage #(text :t.dropdown/no-results)
                 :hideSelectedOptions hide-selected?
                 :options (into-array items)
                 :value (into-array (filter item-selected? items))
                 :onChange #(on-change %)
                 :placeholder (text :t.dropdown/placeholder)}])

(defn guide
  []
  (let [on-change (fn [items] (println "items" items))
        items [{:id 1 :name "Alice"}
               {:id 2 :name "Bob"}
               {:id 3 :name "Carl"}
               {:id 4 :name "Own"}
               {:id 5 :name "Deve"}]]
    [:div
     (component-info dropdown)
     (example "dropdown menu, single-choice, empty"
              [dropdown {:items items
                         :item-key :id
                         :item-label :name
                         :on-change on-change}])
     (example "dropdown menu, single-choice, selected item Bob"
              [dropdown {:items items
                         :item-key :id
                         :item-label :name
                         :item-selected? #(= "Bob" (:name %))
                         :on-change on-change}])
     (example "dropdown menu, multi-choice, several values selected"
              [dropdown {:items items
                         :item-key :id
                         :item-label :name
                         :item-selected? #(contains? #{1 3 5} (% :id))
                         :multi? true
                         :on-change on-change}])
     (example "dropdown menu, multi-choice, several values selected"
              [dropdown {:items items
                         :item-key :id
                         :item-label :name
                         :item-selected? #(contains? #{1 3 5} (% :id))
                         :multi? true
                         :hide-selected? false
                         :on-change on-change}])]))
