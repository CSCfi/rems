(ns rems.table
  "Generic sortable table widget"
  (:require [rems.atoms :refer [sort-symbol]]
            [clojure.string :as string]))

(defn column-header [column-definitions col]
  ((get-in column-definitions [col :header] (constantly ""))))

(defn column-value [column-definitions col item]
  ((get-in column-definitions [col :value]) item))

(defn column-values [column-definitions col item]
  (let [values (get-in column-definitions [col :values])]
    (if values
      (values item)
      [(column-value column-definitions col item)])))

(defn column-class [column-definitions col]
  (get-in column-definitions [col :class] (name col)))

(defn column-sort-value [column-definitions col item]
  ((or (get-in column-definitions [col :sort-value])
       (get-in column-definitions [col :value]))
   item))

(defn- row [column-definitions columns item]
  (into [:tr.action]
        (for [c columns]
          (into [:td {:class   (column-class column-definitions c)
                      :data-th (column-header column-definitions c)}]
                (column-values column-definitions c item)))))

(defn- flip [order]
  (case order
    :asc :desc
    :desc :asc))

(defn- change-sort [old-column old-order new-column]
  (if (= old-column new-column)
    [old-column (flip old-order)]
    [new-column :asc]))

(defn- apply-sorting [column-definitions [col order] items]
  (let [sorted (sort-by #(column-sort-value column-definitions col %) items)]
    (case order
      :asc sorted
      :desc (reverse sorted))))

(defn matches-filter [column-definitions col filter-value item]
  (let [actual-value (column-sort-value column-definitions col item)]
    (string/includes? (.toLowerCase actual-value)
                      (.toLowerCase filter-value))))

(defn matches-filters [column-definitions filters item]
  (every? (fn [[col filter-value]] ()
            (matches-filter column-definitions col filter-value item))
          filters))

(defn apply-filtering [column-definitions filters items]
  (filter #(matches-filters column-definitions filters %) items))

(defn component
  "Table component. Args:

   column-definitions: a map like
     {:column-name {:header (fn [] ...)
                    :value (fn [item] ...)
                    :sort-value (fn [item] ...)
                    :sortable? bool-defaults-to-true
                    :filterable? bool-defaults-to-true
                    :class defaults-to-name-of-column-name-kw}
      ...}
   visible-columns: a sequence of keys that occur in column-definitions
   [sort-column sort-order]: a pair of a colum name and :asc or :desc
   id-function: function for setting react key for row, should return unique values
   set-sorting: callback to call with [col order] when the sort is changed
   items: sequence of items to render
   opts: possibly options with {:class classes for the table}"
  [column-definitions visible-columns [sort-column sort-order] set-sorting id-function items & [opts]]
  [:table.rems-table (when (:class opts) (select-keys opts [:class]))
   (into [:tbody
          (into [:tr]
                (for [c visible-columns]
                  (let [sortable? (get-in column-definitions [c :sortable?] true)
                        filterable? (get-in column-definitions [c :filterable?] true)]
                    [:th
                       [:div.column-header
                          {:on-click (when sortable?
                                       #(set-sorting (change-sort sort-column sort-order c)))}
                          (column-header column-definitions c)
                          " "
                          (when (= c sort-column) (sort-symbol sort-order))]
                       (when filterable?
                         [:input.column-filter ; TODO: event handler
                            {:type "text"
                             :placeholder "Filter"}])])))]
         (map (fn [item] ^{:key (id-function item)} [row column-definitions visible-columns item])
              (->> items
                   (apply-filtering column-definitions {}) ; TODO: parameterize filters
                   (apply-sorting column-definitions [sort-column sort-order]))))])
