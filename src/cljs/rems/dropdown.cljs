(ns rems.dropdown
  (:require [cljsjs.react-select]
            [clojure.string :as str]
            [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn dropdown
  "Single- or multi-choice, searchable dropdown menu.

  `:id` unique id for the input
  `:class` additional classes for the input
  `:items` items shown in dropdown
  `:item-key` getter for the key of an option, used as the id of an item
  `:item-label` getter for the label of an option shown in the dropdown
  `:item-selected?` is this item currently selected?
  `:hide-selected?` should the items that are selected be shown in the dropdown, defaults: false for single value, true for a multiple choice
  `:item-disabled?` is this item currently disabled?
  `:multi?` is this a multiple choice dropdown?
  `:clearable?` should there be a clear selection button?
  `:on-change` called each time the value changes, one or seq"
  [{:keys [id class items item-key item-label item-selected? hide-selected? item-disabled? multi? clearable? on-change]
    :or {item-key identity
         item-label identity
         hide-selected? multi?
         item-selected? (constantly false)
         item-disabled? (constantly false)}}]
  ;; some of the callbacks may be keywords which aren't JS fns so we wrap them in anonymous fns
  [:> js/Select {:className (str/trimr (str "dropdown-container " class))
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
                 :onChange #(on-change (if (array? %) (array-seq %) %))
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
