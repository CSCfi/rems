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
       (get-in column-definitions [col :value])) item))

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

(defn- change-sort-order [old-column old-order new-column]
  (if (= old-column new-column)
    (flip old-order)
    :asc))

(defn- apply-sorting [column-definitions [col order] items]
  (let [sorted (sort-by #(column-sort-value column-definitions col %) items)]
    (case order
      :asc sorted
      :desc (reverse sorted))))

(defn matches-filter [column-definitions col filter-value item]
  (let [actual-value (str (column-sort-value column-definitions col item))]
    (string/includes? (string/lower-case actual-value)
                      (string/lower-case filter-value))))

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
   sorting: sorting and filtering options, for example {:sort-column :name, :sort-order :asc}
   set-sorting: a callback that is called with a new sorting when it changes
   id-function: function for setting react key for row, should return unique values
   items: sequence of items to render
   opts: possibly options with {:class classes for the table}"
  [column-definitions visible-columns {:keys [sort-column sort-order filters] :as sorting} set-sorting id-function items & [opts]]
  [:table.rems-table (when (:class opts) (select-keys opts [:class]))
   (into [:tbody
          (into [:tr]
                (for [column visible-columns]
                  (let [sortable? (get-in column-definitions [column :sortable?] true)
                        filterable? (get-in column-definitions [column :filterable?] true)]
                    [:th
                     [:div.column-header
                      {:on-click (when sortable?
                                   (fn [] (set-sorting (-> sorting
                                                           (assoc :sort-column column)
                                                           (assoc :sort-order (change-sort-order sort-column sort-order column))))))}
                      (column-header column-definitions column)
                      " "
                      (when (= column sort-column)
                        (sort-symbol sort-order))]
                     (when filterable?
                       [:input.column-filter
                        {:type        "text"
                         :placeholder "Filter"
                         :on-input    (fn [event] (set-sorting
                                                    (assoc-in sorting [:filters column] (-> event .-target .-value))))}])])))]
         (map (fn [item] ^{:key (id-function item)} [row column-definitions visible-columns item])
              (->> items
                   (apply-filtering column-definitions filters)
                   (apply-sorting column-definitions [sort-column sort-order]))))])
