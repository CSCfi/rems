(ns rems.table
  "Generic sortable table widget"
  (:require [rems.atoms :refer [sort-symbol search-symbol]]
            [schema.core :as s]
            [clojure.string :as str]))

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
  (let [filter-value-fn (or (get-in column-definitions [col :filter-value])
                            (get-in column-definitions [col :value]))]
    (if filter-value-fn
      (filter-value-fn item)
      (throw (js/Error. (str "No `:filter-value` or `:value` defined for column \"" col "\""))))))

(defn- row [column-definitions columns item]
  (into [:tr.action]
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


(defn matches-filter [column-definitions col filter-value item]
  (let [actual-value (str (column-filter-value column-definitions col item))]
    (str/includes? (str/lower-case actual-value)
                   (str/lower-case filter-value))))

(defn matches-filters [column-definitions filters item]
  (every? (fn [[col filter-value]] ()
            (matches-filter column-definitions col filter-value item))
          filters))

(defn apply-filtering [column-definitions filters items]
  (filter #(matches-filters column-definitions filters %) items))

(s/defschema Applicable
  (s/cond-pre s/Keyword (s/pred fn?)))

(s/defschema TableParams
  {:column-definitions {s/Keyword {(s/optional-key :header) s/Any
                                   (s/optional-key :value) Applicable
                                   (s/optional-key :values) Applicable
                                   (s/optional-key :sortable?)  s/Bool
                                   (s/optional-key :sort-value) Applicable
                                   (s/optional-key :filterable?) s/Bool
                                   (s/optional-key :filter-value) Applicable
                                   (s/optional-key :class) (s/cond-pre s/Str Applicable)}}
   :visible-columns [s/Keyword]
   (s/optional-key :sorting) {(s/optional-key :sort-column) s/Keyword
                              (s/optional-key :sort-order) (s/enum :asc :desc)
                              (s/optional-key :set-sorting) Applicable}
   (s/optional-key :filtering) {(s/optional-key :filters) {s/Keyword s/Str}
                                (s/optional-key :show-filters) s/Bool
                                (s/optional-key :set-filtering) Applicable}
   :id-function Applicable
   :items [s/Any]
   (s/optional-key :class) s/Str})

(defn- filter-toggle [{:keys [show-filters set-filtering] :as filtering}]
  (when filtering
    [:div.rems-table-search-toggle.d-flex.flex-row-reverse
     [:button.btn
      {:class (if show-filters "btn-secondary" "btn-primary")
       :on-click #(set-filtering (update filtering :show-filters not))}
      (search-symbol)]]))

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
        (sort-symbol sort-order))]]))

(defn- column-filter-view [column column-definitions filtering]
  (let [{:keys [show-filters filters set-filtering]} filtering]
    [:th
     (when (get-in column-definitions [column :filterable?] true)
       [:div.column-filter
        [:input
         {:type        "text"
          :name        (str (name column) "-search")
          :value       (str (column filters))
          :placeholder ""
          :on-input    (fn [event]
                         (set-filtering
                          (assoc-in filtering [:filters column] (-> event .-target .-value))))}]
        (when (not= "" (get filters column ""))
          [:div.reset-button.icon-link.fa.fa-backspace
           {:on-click (fn []
                        (set-filtering
                         (assoc-in filtering [:filters column] "")))
            :aria-hidden true}])])]))

(defn- head [{:keys [column-definitions visible-columns sorting filtering id-function items class] :as params}]
  (let [{:keys [show-filters]} filtering]
    [:thead
     (into [:tr]
           (for [column visible-columns]
             [column-header-view column column-definitions sorting]))
     (when show-filters
       (into [:tr]
             (for [column visible-columns]
               [column-filter-view column column-definitions filtering])))]))

(defn- body [{:keys [column-definitions visible-columns sorting filtering id-function items class] :as params}]
  (let [{:keys [sort-column sort-order set-sorting]} sorting
        {:keys [show-filters filters set-filtering]} filtering]
    [:tbody
     (map (fn [item] ^{:key (id-function item)} [row column-definitions visible-columns item])
          (cond->> items
            (and filtering filters) (apply-filtering column-definitions filters)
            (and sorting sort-column) (apply-sorting column-definitions sort-column sort-order)))]))

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
    `:sort-column`      - the column to sort by
    `:sort-order`       - direction of sort (`:asc` or `:desc`)
    `:set-sorting`      - callback that is called when sorting changes

  `:filtering`          - filtering options that can have
    `:filters`          - the filter values by column
    `:show-filters`     - are the filters currently visible?
    `:set-filtering`    - callback that is called when filtering changes

  `:id-function`        - function for setting unique React key for row
  `:items`              -  sequence of items to render
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
