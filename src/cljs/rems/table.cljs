(ns rems.table
  "Generic sortable table widget"
  (:require [clojure.data :refer [diff]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [reagent.core :as reagent]
            [rems.atoms :refer [close-symbol search-symbol sort-symbol]]
            [rems.text :refer [text]]
            [schema.core :as s]))

(defn column-header [column-definitions col]
  ((get-in column-definitions [col :header] (constantly ""))))

(defn column-value [column-definitions col item]
  ((get-in column-definitions [col :value]) item))

(defn column-values [column-definitions col item]
  (let [values (get-in column-definitions [col :values])]
    (if values
      (values item)
      [(column-value column-definitions col item)])))

(defn column-class [column-definitions col item]
  (let [class (get-in column-definitions [col :class] (name col))]
    (cond (string? class) class
          (fn? class) (class item))))

(defn column-sort-value [column-definitions col item]
  (let [sort-value-fn (or (get-in column-definitions [col :sort-value])
                          (get-in column-definitions [col :value]))]
    (if sort-value-fn
      (sort-value-fn item)
      (throw (js/Error. (str "No `:sort-value` or `:value` defined for column \"" col "\""))))))

(defn column-filter-value [column-definitions col item]
  (if (get-in column-definitions [col :filterable?] true)
    (let [filter-value-fn (or (get-in column-definitions [col :filter-value])
                              (get-in column-definitions [col :value]))]
      (if filter-value-fn
        (str (filter-value-fn item))
        (throw (js/Error. (str "No `:filter-value` or `:value` defined for column \"" col "\"")))))
    ""))

(defn- row [id column-definitions columns item]
  (into [:tr.action {:id id}]
        (for [col columns]
          (into [:td {:class (column-class column-definitions col item)
                      :data-th (column-header column-definitions col)}]
                (column-values column-definitions col item)))))

(defn- flip [order]
  (case order
    :desc :asc
    :desc))

(defn- change-sort-order [old-column old-order new-column]
  (if (= old-column new-column)
    (flip old-order)
    :asc))

(defn- apply-sorting [column-definitions sort-column sort-order items]
  (sort-by #(column-sort-value column-definitions sort-column %)
           (case sort-order
             :desc #(compare %2 %1)
             #(compare %1 %2))
           items))

(defn- apply-initial-sorting [column-definitions initial-sort items]
  (reduce (fn [items {:keys [sort-column sort-order]}]
            (apply-sorting column-definitions sort-column sort-order items)) items initial-sort))

(defn match-filter? [column-definitions filter-word item]
  (some (fn [col]
          (str/includes? (str/lower-case (column-filter-value column-definitions col item))
                         (str/lower-case filter-word)))
        (keys column-definitions)))

(deftest test-match-filter?
  (is (match-filter? {:id {:value :id}
                      :description {:value :description}}
                     "bar"
                     {:id "foo"
                      :description "bar"})
      "matches")
  (is (not (match-filter? {:id {:value :id}
                           :description {:value :its-a-trap
                                         :filter-value :description}}
                          "willnotmatch"
                          {:id "foo"
                           :its-a-trap "willnotmatch"
                           :description "bar"}))
      "doesn't match with value if filter-value is provided")
  (is (not (match-filter? {:id {:value :id}
                           :description {:value :description
                                         :filterable? false}}
                          "shouldnotmatch"
                          {:id "foo"
                           :description "shouldnotmatch"}))
      "doesn't match if filterable is false"))

(defn match-filters? [column-definitions filters item]
  (every? (fn [filter-word]
            (match-filter? column-definitions filter-word item))
          (str/split filters #"\s+")))

(defn apply-filtering [column-definitions filters items]
  (filter #(match-filters? column-definitions filters %) items))

(s/defschema Applicable
  (s/cond-pre s/Keyword (s/pred fn?)))

(s/defschema TableParams
  {:column-definitions {s/Keyword {(s/optional-key :header) s/Any
                                   (s/optional-key :value) Applicable
                                   (s/optional-key :values) Applicable
                                   (s/optional-key :sortable?) s/Bool
                                   (s/optional-key :sort-value) Applicable
                                   (s/optional-key :filterable?) s/Bool
                                   (s/optional-key :filter-value) Applicable
                                   (s/optional-key :class) (s/cond-pre s/Str Applicable)}}
   :visible-columns [s/Keyword]
   (s/optional-key :sorting) {(s/optional-key :initial-sort) [{:sort-column s/Keyword
                                                               (s/optional-key :sort-order) (s/enum :asc :desc)}]
                              (s/optional-key :sort-column) s/Keyword
                              (s/optional-key :sort-order) (s/enum :asc :desc)
                              (s/optional-key :set-sorting) Applicable}
   (s/optional-key :filtering) {(s/optional-key :filters) s/Str
                                (s/optional-key :filters-new) s/Str
                                (s/optional-key :show-filters) s/Bool
                                (s/optional-key :set-filtering) Applicable}
   :id-function Applicable
   :items [s/Any]
   (s/optional-key :class) s/Str})

(defn- filter-view [filtering]
  (let [{:keys [filters-new set-filtering]} filtering
        update-current-filters (fn [event]
                                 (set-filtering (assoc filtering :filters filters-new)))]
    [:div.flex-grow-1.d-flex
     [:input.flex-grow-1
      {:type "text"
       :name "table-search"
       :default-value filters-new
       :placeholder ""
       :aria-label (text :t.search/search-parameters)
       :on-change (fn [event]
                    (set-filtering (assoc filtering :filters-new (-> event .-target .-value))))
       :on-blur update-current-filters
       :on-key-press (fn [event]
                       (when (= 13 (.-which event)) ; enter
                         (.preventDefault event)
                         (update-current-filters event)))}]
     [:button.btn.btn-primary
      {:type "button"
       :on-click update-current-filters
       :aria-label (text :t.search/search)}
      [search-symbol]]]))

(defn- filter-toggle [{:keys [show-filters set-filtering] :as filtering}]
  (when filtering
    [:div.rems-table-search-toggle.d-flex.flex-row-reverse
     [:button.btn
      {:type "button"
       :class (if show-filters "btn-secondary" "btn-primary")
       :aria-label (if show-filters (text :t.search/close-search) (text :t.search/open-search))
       ;; TODO: focus needs to be moved to the search field after opening it, especially for screen readers
       :on-click #(set-filtering (-> filtering
                                     (update :show-filters not)
                                     (assoc :filters ""
                                            :filters-new "")))}
      (if show-filters [close-symbol] [search-symbol])]
     (when show-filters
       [filter-view filtering])]))

(defn- column-header-view [column column-definitions sorting]
  (let [{:keys [sort-column sort-order set-sorting]} sorting
        sortable? (get-in column-definitions [column :sortable?] true)]
    [:th
     [:div.column-header
      (when (and sortable? set-sorting)
        {:on-click (fn []
                     (set-sorting (-> sorting
                                      (assoc :sort-column column)
                                      (assoc :sort-order (change-sort-order sort-column sort-order column)))))})
      (column-header column-definitions column)
      " "
      (when (= column sort-column)
        [sort-symbol sort-order])]]))

(defn- head [{:keys [column-definitions visible-columns sorting filtering id-function items class] :as params}]
  [:thead
   (into [:tr]
         (for [column visible-columns]
           [column-header-view column column-definitions sorting]))])

(defn- comparable-params [params]
  (let [params-without-fns (-> params
                               (dissoc :id-function)
                               (dissoc :column-definitions)
                               (update :filtering dissoc :set-filtering :filters-new)
                               (update :sorting dissoc :set-sorting))]
    [(dissoc params-without-fns :items)
     (:items params)]))

(defn- body
  "Create a Form 3 component because we don't want to compare potentially anonymous callback fns in the params. It would cause us to re-render every time."
  [params]
  (reagent/create-class
   {:display-name "table-body"
    :should-component-update (fn [this [_ old] [_ new]]
                               (not= (comparable-params old)
                                     (comparable-params new)))
    :reagent-render (fn [{:keys [column-definitions visible-columns sorting filtering id-function items class]}]
                      (let [{:keys [initial-sort sort-column sort-order]} sorting
                            {:keys [show-filters filters]} filtering]
                        [:tbody
                         (map (fn [item] ^{:key (id-function item)} [row (id-function item) column-definitions visible-columns item])
                              (cond->> items
                                (and initial-sort (not sort-column)) (apply-initial-sorting column-definitions initial-sort)
                                (and show-filters filters) (apply-filtering column-definitions filters)
                                (and sorting sort-column) (apply-sorting column-definitions sort-column sort-order)))]))}))

(defn component
  "Table component.

  `:column-definitions` - a map like {:column-id definition} where definition is
    `:header`           - (fn [item] ...) column header component
    `:value`            - (fn [item] ...) single value component
    `:values`           - (fn [item] ...) sequence of value components
    `:sortable?`        - is the column sortable?
    `:sort-value`       - (fn [item] ...) value to sort by
    `:filterable?`      - is the column filterable?
    `:filter-value`     - (fn [item] ...) value to filter by
    `:class`            - class for the column (defaults to column name),
                          can be (fn [item] ...)

  `:visible-columns`    - a sequence of keys that occur in column-definitions

  `:sorting`            - sorting options map with keys
    `:initial-sort`     - seq of {`:sort-column` :xxx} and optionally `:sort-order` to initially sort by
    `:sort-column`      - the column to sort by
    `:sort-order`       - direction of sort (`:asc` or `:desc`)
    `:set-sorting`      - callback that is called when sorting changes

  `:filtering`          - filtering options that can have
    `:filters`          - the current filter values
    `:filters-new`      - the new filter values (internal)
    `:show-filters`     - are the filters currently visible?
    `:set-filtering`    - callback that is called when filtering changes

  `:id-function`        - function for setting unique React key for row
  `:items`              - sequence of items to render
  `:class`              - class for the table

  See also `TableParams`."
  [{:keys [column-definitions visible-columns sorting filtering id-function items class] :as params}]
  {:pre [(s/validate TableParams params)]}
  [:div
   [filter-toggle filtering]
   [:div.table-border
    [:table.rems-table (when class {:class class})
     [head params]
     [body params]]]])
