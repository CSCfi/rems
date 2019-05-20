(ns rems.table2
  (:require [clojure.string :as str]
            [re-frame.core :as rf]))

;; TODO: componentize
(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))
(rf/reg-sub ::sorting (fn [db _] (::sorting db {:sort-order :asc
                                                :sort-column :name})))

;; TODO: componentize
(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(rf/reg-sub
 ::sorted-rows
 (fn [[_ data-key] _]
   [(rf/subscribe [data-key])
    (rf/subscribe [::sorting data-key])])
 (fn [[rows sorting] [_ data-key]]
   (->> rows
        (sort-by #(get-in % [(:sort-column sorting) :sort-value])
                 (case (:sort-order sorting)
                   :desc #(compare %2 %1)
                   #(compare %1 %2))))))

(rf/reg-sub
 ::sorted-and-filtered-rows
 (fn [[_ data-key] _]
   [(rf/subscribe [::sorted-rows data-key])
    (rf/subscribe [::filtering data-key])])
 (fn [[rows filtering] [_ data-key]]
   (let [needle (str/lower-case (str (:filters filtering)))]
     (->> rows
          (map (fn [row]
                 ;; TODO: componentize
                 (assoc row ::display-row? (str/includes? (get-in row [:name :filter-value])
                                                          needle))))))))

(defn filter-field [data-key]
  (let [filtering (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))]
    [rems.table/filter-toggle filtering]))

(defn table [data-key columns]
  (let [rows @(rf/subscribe [::sorted-and-filtered-rows data-key])
        sorting (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
        language @(rf/subscribe [:language])]
    [:div.table-border
     [:table.rems-table.catalogue
      [:thead
       [:tr
        (for [column columns]
          [:th
           (when (:sortable? column)
             {:on-click (fn []
                          (rf/dispatch [::set-sorting (-> sorting
                                                          (assoc :sort-column (:key column))
                                                          (assoc :sort-order (rems.table/change-sort-order (:sort-column sorting) (:sort-order sorting) (:key column))))]))})
           (:title column)
           " "
           (when (:sortable? column)
             (when (= (:key column) (:sort-column sorting))
               [rems.atoms/sort-symbol (:sort-order sorting)]))])]]
      [:tbody {:key language} ; performance optimization: rebuild instead of update existing components
       (for [row rows]
         (into [:tr {:key (:row-id row)
                     ;; performance optimization: hide DOM nodes instead of destroying them
                     :style {:display (if (::display-row? row)
                                        "table-row"
                                        "none")}}]
               (for [column columns]
                 (get-in row [(:key column) :td]))))]]]))
