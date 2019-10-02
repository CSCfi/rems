(ns rems.table
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :refer [close-symbol search-symbol sort-symbol]]
            [rems.focus :as focus]
            [rems.search :as search]
            [rems.text :refer [text-format]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-db
 ::reset
 (fn [db _]
   (dissoc db ::sorting ::filtering ::max-rows)))

(defn- flip [order]
  (case order
    :desc :asc
    :desc))

(defn- change-sort-order [old-column old-order new-column]
  (if (= old-column new-column)
    (flip old-order)
    :asc))

(rf/reg-event-db
 ::toggle-sorting
 (fn [db [_ table sort-column]]
   (update-in db [::sorting (:id table)]
              (fn [sorting]
                (-> sorting
                    (assoc :sort-column sort-column)
                    (assoc :sort-order (change-sort-order (:sort-column sorting)
                                                          (:sort-order sorting)
                                                          sort-column)))))))

(rf/reg-sub
 ::sorting
 (fn [db [_ table]]
   (or (get-in db [::sorting (:id table)])
       {:sort-order (or (:default-sort-order table)
                        :asc)
        :sort-column (or (:default-sort-column table)
                         (-> table :columns first :key))})))

(rf/reg-event-db
 ::set-filtering
 (fn [db [_ table filtering]]
   (assoc-in db [::filtering (:id table)] filtering)))

(rf/reg-sub
 ::filtering
 (fn [db [_ table]]
   (get-in db [::filtering (:id table)])))

(rf/reg-event-db
 ::show-all-rows
 (fn [db [_ table]]
   (assoc-in db [::max-rows (:id table)] js/Number.MAX_SAFE_INTEGER)))

(rf/reg-sub
 ::max-rows
 (fn [db [_ table]]
   (or (get-in db [::max-rows (:id table)])
       50)))

(defn- default-if-missing [m k make-default]
  (if (contains? m k)
    m
    (assoc m k (make-default m))))

(defn apply-row-defaults [row]
  (->> row
       (map (fn [[column opts]]
              [column
               (if (= :key column) ; not a column, but the row key
                 opts
                 (-> opts
                     (default-if-missing :sort-value (fn [opts]
                                                       (let [val (:value opts)]
                                                         (if (string? val)
                                                           (str/lower-case val)
                                                           val))))
                     (default-if-missing :display-value (comp str :value))
                     (default-if-missing :filter-value (fn [opts]
                                                         (if (string? (:display-value opts))
                                                           (str/lower-case (:display-value opts))
                                                           "")))
                     (default-if-missing :td (fn [opts]
                                               [:td {:class (name column)}
                                                (:display-value opts)]))))]))
       (into {})))

(rf/reg-sub
 ::rows
 (fn [[_ table] _]
   [(rf/subscribe (:rows table))])
 (fn [[rows] _]
   (map apply-row-defaults rows)))

(rf/reg-sub
 ::sorted-rows
 (fn [[_ table] _]
   [(rf/subscribe [::rows table])
    (rf/subscribe [::sorting table])])
 (fn [[rows sorting] _]
   (->> rows
        (sort-by #(get-in % [(:sort-column sorting) :sort-value])
                 (case (:sort-order sorting)
                   :desc #(compare %2 %1)
                   #(compare %1 %2))))))

(defn- sortable? [column]
  (:sortable? column true))

(defn- filterable? [column]
  (:filterable? column true))

(defn- display-row? [row filtered-columns search-terms]
  (or (empty? filtered-columns) ; table has no filtering enabled
      (let [filtered-values (map (fn [column]
                                   (str (get-in row [(:key column) :filter-value])))
                                 filtered-columns)]
        (every? (fn [search-term]
                  (some #(str/includes? % search-term) filtered-values))
                search-terms))))

(defn parse-search-terms [s]
  (->> (re-seq #"\S+" (str s))
       (map str/lower-case)))

(rf/reg-sub
 ::sorted-and-filtered-rows
 (fn [[_ table] _]
   [(rf/subscribe [::sorted-rows table])
    (rf/subscribe [::filtering table])])
 (fn [[rows filtering] [_ table]]
   (let [search-terms (parse-search-terms (:filters filtering))
         columns (->> (:columns table)
                      (filter filterable?))]
     (->> rows
          (map (fn [row]
                 ;; performance optimization: hide DOM nodes instead of destroying them
                 (assoc row ::display-row? (display-row? row columns search-terms))))))))

(defn search [table]
  (let [filtering @(rf/subscribe [::filtering table])
        on-search (fn [value]
                    (rf/dispatch [::set-filtering table (assoc filtering :filters value)]))]
    [search/search-field {:id (str (name (:id table)) "-search")
                          :on-search on-search
                          :searching? false}]))

(defn- table-header [table]
  (let [sorting @(rf/subscribe [::sorting table])]
    (into [:tr]
          (for [column (:columns table)]
            [:th
             (when (sortable? column)
               {:on-click #(rf/dispatch [::toggle-sorting table (:key column)])})
             (:title column)
             " "
             (when (sortable? column)
               (when (= (:key column) (:sort-column sorting))
                 [sort-symbol (:sort-order sorting)]))]))))

(defn- table-row [row table]
  (into [:tr {:data-row (:key row)
              ;; performance optimization: hide DOM nodes instead of destroying them
              :style {:display (if (::display-row? row)
                                 "table-row"
                                 "none")}}]
        (for [column (:columns table)]
          (get-in row [(:key column) :td]))))

(defn table [table]
  (let [rows @(rf/subscribe [::sorted-and-filtered-rows table])
        language @(rf/subscribe [:language])
        max-rows @(rf/subscribe [::max-rows table])
        ;; When showing all rows, table-row is responsible for filtering displayed rows,
        ;; but with truncation the visible rows need to be filtered before truncation.
        rows (if (< max-rows (count rows))
               (filter ::display-row? rows)
               rows)]
    [:div.table-border
     [:table.rems-table {:class (:id table)}
      [:thead
       [table-header table]]
      [:tbody {:key language} ; performance optimization: rebuild instead of update existing components
       (for [row (take max-rows rows)]
         ^{:key (:key row)} [table-row row table])]
      (when (< max-rows (count rows))
        [:tfoot
         [:tr [:td {:col-span (count (:columns table))
                    :style {:text-align :center}}
               [:button.btn.btn-primary {:type :button
                                         :on-click (fn []
                                                     (rf/dispatch [::show-all-rows table])
                                                     (let [next-row (:key (nth rows max-rows))]
                                                       (focus/focus-element-async (str "table.rems-table." (name (:id table))
                                                                                       " > tbody > tr[data-row='" next-row "'] > td"))))}
                (text-format :t.table/show-all-n-rows (count rows))]]]])]]))

(defn guide []


  [:div
   (component-info table)
   ;; slight abuse of example macro, but it works since reg-sub returns a fn which reagent doesn't render
   (example "data for examples"
            (rf/reg-sub
             ::example-table-rows
             (fn [_ _]
               [{:id {:value 1}
                 :first-name {:value "Cody"}
                 :last-name {:value "Turner"}}
                {:id {:value 2}
                 :first-name {:value "Melanie"}
                 :last-name {:value "Palmer"}}
                {:id {:value 3}
                 :first-name {:value "Henry"}
                 :last-name {:value "Herring"}}])))
   (example "static table"
            (let [example1 {:id ::example1
                            :columns [{:key :first-name
                                       :title "First name"
                                       :sortable? false
                                       :filterable? false}
                                      {:key :last-name
                                       :title "Last name"
                                       :sortable? false
                                       :filterable? false}]
                            :rows [::example-table-rows]
                            :default-sort-column :first-name}]
              [table example1]))
   (example "sortable and filterable table"
            (let [example2 {:id ::example2
                            :columns [{:key :first-name
                                       :title "First name"}
                                      {:key :last-name
                                       :title "Last name"}
                                      {:key :commands
                                       :sortable? false
                                       :filterable? false}]
                            :rows [::example-table-rows]
                            :default-sort-column :first-name}]
              [:div
               [search example2]
               [table example2]]))
   (example "richer example data"
            (do
              (rf/reg-sub
               ::example-rich-table-rows
               (fn [_ _]
                 [{:team {:display-value "Team Hawks"
                          :filter-value "hawks"
                          :sort-value "0000hawks"}
                   :points {:value 3
                            :display-value "-> 3 <-"}}
                  {:team {:value "Eagles"
                          :td [:td.eagles-are-best [:em "Eagles"]]}
                   :points {:value 4}}
                  {:team {:value "Ravens"}
                   :points {:value 0}}]))
              (let [example3 {:id ::example3
                              :columns [{:key :team
                                         :title "Team"}
                                        {:key :points
                                         :title "Points"}]
                              :rows [::example-rich-table-rows]}]
                [:div
                 [search example3]
                 [table example3]])))])
