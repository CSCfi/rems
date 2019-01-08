(ns rems.table
  "Generic sortable table widget"
  (:require [rems.atoms :refer [sort-symbol search-symbol]]
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
  ((or (get-in column-definitions [col :sort-value])
       (get-in column-definitions [col :value])) item))

(defn- row [{:keys [row-class]} column-definitions columns item]
  (into [:tr {:class (cond (string? row-class) row-class
                           (fn? row-class) (row-class item))}]
        (for [col columns]
          (into [:td {:class (column-class column-definitions col item)
                      :data-th (column-header column-definitions col)}]
                (column-values column-definitions col item)))))

(defn- flip [order]
  (case order
    :asc :desc
    :desc :asc))

(defn- change-sort-order [old-column old-order new-column]
  (if (= old-column new-column)
    (flip old-order)
    :asc))

(defn- apply-sorting [column-definitions sort-column sort-order items]
  (let [sorted (sort-by #(column-sort-value column-definitions sort-column %) items)]
    (case sort-order
      :asc sorted
      :desc (reverse sorted))))

(defn matches-filter [column-definitions col filter-value item]
  (let [actual-value (str (column-sort-value column-definitions col item))]
    (str/includes? (str/lower-case actual-value)
                   (str/lower-case filter-value))))

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
   set-sorting: a callback that is called with a new sorting and filtering when it changes
   id-function: function for setting react key for row, should return unique values
   items: sequence of items to render
   opts: possibly options with {:class classes for the table}"
  [column-definitions visible-columns {:keys [sort-column sort-order filters show-filters] :as sorting} set-sorting id-function items & [opts]]
  [:div
   (when filters
     [:div.rems-table-search-toggle.d-flex.flex-row-reverse
      [:div.btn
       {:class (if show-filters "btn-secondary" "btn-primary")
        :on-click (fn [] (when set-sorting
                           (set-sorting (assoc sorting :show-filters (not show-filters)))))}
       (search-symbol)]])
   [:table.rems-table (when (:class opts) (select-keys opts [:class]))
    [:thead
     (into [:tr]
           (for [column visible-columns]
             (let [sortable? (get-in column-definitions [column :sortable?] true)]
               [:th
                [:div.column-header
                 {:on-click (when (and sortable? set-sorting)
                              (fn [] (set-sorting (-> sorting
                                                      (assoc :sort-column column)
                                                      (assoc :sort-order (change-sort-order sort-column sort-order column))))))}
                 (column-header column-definitions column)
                 " "
                 (when (= column sort-column)
                   (sort-symbol sort-order))]])))
     (when show-filters
       (into [:tr]
             (for [column visible-columns]
               [:th
                (when (get-in column-definitions [column :filterable?] true)
                  [:div.column-filter
                   [:input
                    {:type        "text"
                     :name        (str (name column) "-search")
                     :value       (str (column filters))
                     :placeholder ""
                     :on-input    (fn [event] (set-sorting
                                               (assoc-in sorting [:filters column] (-> event .-target .-value))))}]
                   (when (not= "" (get filters column ""))
                     [:span.reset-button.icon-link.fa.fa-backspace
                      {:on-click (fn [] (set-sorting
                                         (assoc-in sorting [:filters column] "")))
                       :aria-hidden true}])])])))]
    (into [:tbody]
          (map (fn [item] ^{:key (id-function item)} [row (select-keys opts [:row-class]) column-definitions visible-columns item])
               (cond->> items
                        filters (apply-filtering column-definitions filters)
                        sorting (apply-sorting column-definitions sort-column sort-order))))]])
