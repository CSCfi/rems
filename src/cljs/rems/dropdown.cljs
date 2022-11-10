(ns rems.dropdown
  (:require ["react-select" :default Select]
            ["react-select/async" :default AsyncSelect]
            [clojure.string :as str]
            [medley.core :refer [assoc-some]]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

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
  `:disabled?` is the whole input disabled?
  `:multi?` is this a multiple choice dropdown?
  `:clearable?` should there be a clear selection button?
  `:placeholder` text to show when nothing is selected, defaults to (text :t.dropdown/placeholder)
  `:on-change` called each time the value changes, one or seq"
  [{:keys [id class items item-key item-label item-selected? hide-selected? item-disabled? multi? clearable? placeholder on-change disabled?]
    :or {item-key identity
         item-label identity
         hide-selected? multi?
         item-selected? (constantly false)
         item-disabled? (constantly false)}}]
  ;; some of the callbacks may be keywords which aren't JS fns so we wrap them in anonymous fns
  [:> Select {:className (str/trimr (str "dropdown-container " class))
              :classNamePrefix "dropdown-select"
              :getOptionLabel #(item-label %)
              :getOptionValue #(item-key %)
              :inputId id
              :isMulti multi?
              :isClearable clearable?
              :isDisabled disabled?
              :isOptionDisabled #(item-disabled? %)
              :maxMenuHeight 200
              :noOptionsMessage #(text :t.dropdown/no-results)
              :hideSelectedOptions hide-selected?
              :options (into-array items)
              :value (into-array (filter item-selected? items))
              :onChange #(on-change (if (array? %) (array-seq %) %))
              :placeholder (or placeholder (text :t.dropdown/placeholder))}])

(defn async-dropdown
  "Single- or multi-choice, searchable dropdown menu with support for asynchronous data loading.

  `:id` unique id for the input
  `:class` additional classes for the input
  `:item-key` getter for the key of an option, used as the id of an item
  `:item-label` getter for the label of an option shown in the dropdown
  `:hide-selected?` should the items that are selected be shown in the dropdown, defaults: false for single value, true for a multiple choice
  `:item-selected?` is this item currently selected?
  `:item-disabled?` is this item currently disabled?
  `:disabled?` is the whole input disabled?
  `:multi?` is this a multiple choice dropdown?
  `:clearable?` should there be a clear selection button?
  `:placeholder` text to show when nothing is selected, defaults to (text :t.dropdown/placeholder)
  `:on-change` called each time the value changes, one or seq
  `:on-load-options` function called with :query-string and :on-data keys when dropdown should load new data"
  [{:keys [id class items item-key item-label hide-selected? item-disabled? disabled? multi? clearable? placeholder on-change on-load-options]
    :or {item-key identity
         item-label identity
         hide-selected? multi?
         item-disabled? (constantly false)}}]
  ;; some of the callbacks may be keywords which aren't JS fns so we wrap them in anonymous fns
  [:> AsyncSelect (-> {:className (str/trimr (str "dropdown-container " class))
                       :classNamePrefix "dropdown-select-async"
                       :getOptionLabel #(item-label (js->clj % :keywordize-keys true))
                       :getOptionValue #(item-key (js->clj % :keywordize-keys true))
                       :inputId id
                       :isMulti multi?
                       :isClearable clearable?
                       :isDisabled disabled?
                       :isOptionDisabled #(item-disabled? (js->clj % :keywordize-keys true))
                       :maxMenuHeight 200
                       :noOptionsMessage #(text :t.dropdown/no-results)
                       :hideSelectedOptions hide-selected?
                       :onChange #(let [items (js->clj % :keywordize-keys true)]
                                    (on-change (if (array? items) (array-seq items) items)))
                       :placeholder (or placeholder (text :t.dropdown/placeholder))
                       :loadOptions (fn [query-string callback]
                                      (on-load-options {:query-string query-string
                                                        :on-data #(callback (clj->js %))}))
                       :loadingMessage #(text :t.dropdown/loading)}
                      (assoc-some :value (when (seq items) (into-array items))))])

(defn guide
  []
  (let [on-change (fn [items] (println "items" items))]
    [:div
     (component-info dropdown)
     (example "dropdown menu, single-choice, empty"

              (def example-items [{:id 1 :name "Alice"}
                                  {:id 2 :name "Bob"}
                                  {:id 3 :name "Carl"}
                                  {:id 4 :name "Own"}
                                  {:id 5 :name "Deve"}])

              [dropdown {:items example-items
                         :item-key :id
                         :item-label :name
                         :on-change on-change}])
     (example "dropdown menu, single-choice, selected item Bob"
              [dropdown {:items example-items
                         :item-key :id
                         :item-label :name
                         :item-selected? #(= "Bob" (:name %))
                         :on-change on-change}])

     (example "dropdown menu, multi-choice, several values selected"
              [dropdown {:items example-items
                         :item-key :id
                         :item-label :name
                         :item-selected? #(contains? #{1 3 5} (% :id))
                         :multi? true
                         :on-change on-change}])
     (example "disabled dropdown menu, multi-choice, several values selected"
              [dropdown {:items example-items
                         :item-key :id
                         :item-label :name
                         :item-selected? #(contains? #{1 3 5} (% :id))
                         :multi? true
                         :disabled? true
                         :on-change on-change}])
     (example "dropdown menu, multi-choice, several values selected, hide selected"
              [dropdown {:items example-items
                         :item-key :id
                         :item-label :name
                         :item-selected? #(contains? #{1 3 5} (% :id))
                         :multi? true
                         :hide-selected? false
                         :on-change on-change}])

     (component-info async-dropdown)
     (example "async dropdown menu, single choice, empty"
              [async-dropdown {:item-key :id
                               :item-label :name
                               :on-change on-change
                               :on-load-options (fn [{:keys [_ on-data]}]
                                                  (js/setTimeout #(on-data example-items) 500))}])
     (example "async dropdown menu, multi-choice, empty"
              [async-dropdown {:item-key :id
                               :item-label :name
                               :multi? true
                               :on-change on-change
                               :on-load-options (fn [{:keys [_ on-data]}]
                                                  (js/setTimeout #(on-data example-items) 500))}])
     (example "async dropdown menu, multi-choice, several values selected"
              [async-dropdown {:item-key :id
                               :item-label :name
                               :items (take 2 example-items)
                               :multi? true
                               :on-change on-change
                               :on-load-options (fn [{:keys [_ on-data]}]
                                                  (js/setTimeout #(on-data example-items) 500))}])]))
