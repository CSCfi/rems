(ns rems.table2
  (:require [clojure.string :as str]
            [re-frame.core :as rf]))

(rf/reg-event-db
 ::set-sorting
 (fn [db [_ spec sorting]]
   (assoc-in db [::sorting (:id spec)] sorting)))

(rf/reg-sub
 ::sorting
 (fn [db [_ spec]]
   (or (get-in db [::sorting (:id spec)])
       {:sort-order :asc
        :sort-column (:default-sort-column spec)})))

(rf/reg-event-db
 ::set-filtering
 (fn [db [_ spec filtering]]
   (assoc-in db [::filtering (:id spec)] filtering)))

(rf/reg-sub
 ::filtering
 (fn [db [_ spec]]
   (get-in db [::filtering (:id spec)])))

(rf/reg-sub
 ::sorted-rows
 (fn [[_ spec] _]
   [(rf/subscribe [(:rows spec)])
    (rf/subscribe [::sorting spec])])
 (fn [[rows sorting] _]
   (->> rows
        (sort-by #(get-in % [(:sort-column sorting) :sort-value])
                 (case (:sort-order sorting)
                   :desc #(compare %2 %1)
                   #(compare %1 %2))))))

(defn- display-row? [row columns filters]
  (some (fn [column]
          (str/includes? (get-in row [(:key column) :filter-value])
                         filters))
        columns))

(rf/reg-sub
 ::sorted-and-filtered-rows
 (fn [[_ spec] _]
   [(rf/subscribe [::sorted-rows spec])
    (rf/subscribe [::filtering spec])])
 (fn [[rows filtering] [_ spec]]
   (let [filters (str/lower-case (str (:filters filtering)))
         columns (->> (:columns spec)
                      (filter :filterable?))]
     (->> rows
          (map (fn [row]
                 (assoc row ::display-row? (display-row? row columns filters))))))))

(defn filter-field [spec]
  (let [filtering @(rf/subscribe [::filtering spec])
        filtering (assoc filtering
                         :set-filtering #(rf/dispatch [::set-filtering spec (dissoc % :set-filtering)]))]
    [rems.table/filter-toggle filtering]))

(defn table [spec]
  (let [rows @(rf/subscribe [::sorted-and-filtered-rows spec])
        sorting (assoc @(rf/subscribe [::sorting spec]) :set-sorting #(rf/dispatch [::set-sorting spec %]))
        language @(rf/subscribe [:language])]
    [:div.table-border
     [:table.rems-table.catalogue
      [:thead
       (into [:tr]
             (for [column (:columns spec)]
               [:th
                (when (:sortable? column)
                  {:on-click (fn []
                               (rf/dispatch [::set-sorting spec (-> sorting
                                                                    (assoc :sort-column (:key column))
                                                                    (assoc :sort-order (rems.table/change-sort-order (:sort-column sorting) (:sort-order sorting) (:key column))))]))})
                (:title column)
                " "
                (when (:sortable? column)
                  (when (= (:key column) (:sort-column sorting))
                    [rems.atoms/sort-symbol (:sort-order sorting)]))]))]
      [:tbody {:key language} ; performance optimization: rebuild instead of update existing components
       (for [row rows]
         (into [:tr {:key (:row-id row)
                     ;; performance optimization: hide DOM nodes instead of destroying them
                     :style {:display (if (::display-row? row)
                                        "table-row"
                                        "none")}}]
               (for [column (:columns spec)]
                 (get-in row [(:key column) :td]))))]]]))
