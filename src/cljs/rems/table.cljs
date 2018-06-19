(ns rems.table
  "Generic sortable table widget"
  (:require [rems.atoms :refer [sort-symbol]]))

(defn column-header [column-definitions col]
  ((get-in column-definitions [col :header] (constantly ""))))

(defn column-value [column-definitions col app]
  ((get-in column-definitions [col :value]) app))

(defn column-class [column-definitions col]
  (get-in column-definitions [col :class] (name col)))

(defn column-sort-value [column-definitions col app]
  ((or (get-in column-definitions [col :sort-value])
       (get-in column-definitions [col :value]))
   app))

(defn- row [column-definitions columns app]
  (into [:tr.action]
        (for [c columns]
          [:td {:class (column-class column-definitions c)
                :data-th (column-header column-definitions c)}
           (column-value column-definitions c app)])))

(defn- flip [order]
  (case order
    :asc :desc
    :desc :asc))

(defn- change-sort [old-column old-order new-column]
  (if (= old-column new-column)
    [old-column (flip old-order)]
    [new-column :asc]))

(defn- apply-sorting [column-definitions [col order] apps]
  (let [sorted (sort-by #(column-sort-value column-definitions col %) apps)]
    (case order
      :asc sorted
      :desc (reverse sorted))))

(defn component
  "Table component. Args:

   column-definitions: a map like
     {:column-name {:header (fn [] ...)
                    :value (fn [item] ...)
                    :sort-value (fn [item] ...)
                    :sortable? bool-defaults-to-true
                    :class defaults-to-name-of-column-name-kw}
      ...}
   visible-columns: a sequence of keys that occur in column-definitions
   [sort-column sort-order]: a pair of a colum name and :asc or :desc
   id-function: function for setting react key for row, should return unique values
   set-sorting: callback to call with [col order] when the sort is changed
   items: sequence of items to render
   opts: possibly options wiht {:class classes for the table}"
  [column-definitions visible-columns [sort-column sort-order] set-sorting id-function items & [opts]]
  [:table.rems-table (when (:class opts) (select-keys opts [:class]))
   (into [:tbody
          (into [:tr]
                (for [c visible-columns]
                  [:th
                   {:on-click (when (get-in column-definitions [c :sortable?] true)
                                #(set-sorting (change-sort sort-column sort-order c)))}
                   (column-header column-definitions c)
                   " "
                   (when (= c sort-column) (sort-symbol sort-order))]))]
         (map (fn [item] ^{:key (id-function item)} [row column-definitions visible-columns item])
              (apply-sorting column-definitions [sort-column sort-order] items)))])
