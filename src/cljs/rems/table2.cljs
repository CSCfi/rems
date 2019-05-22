(ns rems.table2
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :refer [close-symbol search-symbol sort-symbol]]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

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

(rf/reg-sub
 ::sorted-rows
 (fn [[_ table] _]
   [(rf/subscribe (:rows table))
    (rf/subscribe [::sorting table])])
 (fn [[rows sorting] _]
   (->> rows
        (sort-by #(get-in % [(:sort-column sorting) :sort-value])
                 (case (:sort-order sorting)
                   :desc #(compare %2 %1)
                   #(compare %1 %2))))))

(defn- display-row? [row filtered-columns filters]
  (if (empty? filtered-columns)
    true ; table has no filtering enabled
    (some (fn [column]
            (str/includes? (str (get-in row [(:key column) :filter-value]))
                           filters))
          filtered-columns)))

(rf/reg-sub
 ::sorted-and-filtered-rows
 (fn [[_ table] _]
   [(rf/subscribe [::sorted-rows table])
    (rf/subscribe [::filtering table])])
 (fn [[rows filtering] [_ table]]
   (let [filters (str/lower-case (str (:filters filtering)))
         columns (->> (:columns table)
                      (filter :filterable?))]
     (->> rows
          (map (fn [row]
                 ;; performance optimization: hide DOM nodes instead of destroying them
                 (assoc row ::display-row? (display-row? row columns filters))))))))

(defn search [table]
  (let [filtering @(rf/subscribe [::filtering table])
        on-search (fn [event]
                    (rf/dispatch [::set-filtering table (-> filtering
                                                            (assoc :filters (-> event .-target .-value)))]))
        ;; TODO: focus needs to be moved to the search field after opening it, especially for screen readers
        on-toggle (fn [_event]
                    (rf/dispatch [::set-filtering table (-> filtering
                                                            (update :show-filters not)
                                                            (assoc :filters ""))]))]
    (if (:show-filters filtering)
      [:div.rems-table-search-toggle.d-flex.flex-row
       [:div.flex-grow-1.d-flex
        [:input.flex-grow-1 {:type :text
                             :default-value (:filters filtering)
                             :aria-label (text :t.search/search-parameters)
                             :on-change on-search}]]
       [:button.btn.btn-secondary {:type :button
                                   :aria-label (text :t.search/close-search)
                                   :on-click on-toggle}
        [close-symbol]]]
      [:div.rems-table-search-toggle.d-flex.flex-row-reverse
       [:button.btn.btn-primary {:type :button
                                 :aria-label (text :t.search/open-search)
                                 :on-click on-toggle}
        [search-symbol]]])))

(defn- table-header [table]
  (let [sorting @(rf/subscribe [::sorting table])]
    (into [:tr]
          (for [column (:columns table)]
            [:th
             (when (:sortable? column)
               {:on-click #(rf/dispatch [::toggle-sorting table (:key column)])})
             (:title column)
             " "
             (when (:sortable? column)
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
        language @(rf/subscribe [:language])]
    [:div.table-border
     [:table.rems-table {:class (:id table)}
      [:thead
       [table-header table]]
      [:tbody {:key language} ; performance optimization: rebuild instead of update existing components
       (for [row rows]
         ^{:key (:key row)} [table-row row table])]]]))

(defn guide []
  (rf/reg-sub
   ::example-table-rows
   (fn [_ _]
     (->> [{:id 1
            :first-name "Cody"
            :last-name "Turner"}
           {:id 2
            :first-name "Melanie"
            :last-name "Palmer"}
           {:id 3
            :first-name "Henry"
            :last-name "Herring"}
           {:id 4
            :first-name "Reagan"
            :last-name "Melton"}]
          (map (fn [person]
                 (let [{:keys [id first-name last-name]} person]
                   {:key id
                    :first-name {:td [:td.first-name first-name]
                                 :sort-value first-name
                                 :filter-value (str/lower-case first-name)}
                    :last-name {:td [:td.last-name last-name]
                                :sort-value last-name
                                :filter-value (str/lower-case last-name)}
                    :commands {:td [:td.commands
                                    [:button.btn.btn-primary
                                     {:type :button
                                      :on-click (fn [] (js/alert (str "View user " id)))}
                                     "View"]
                                    [:button.btn.btn-secondary
                                     {:type :button
                                      :on-click (fn [] (js/alert (str "Delete user " id)))}
                                     "Delete"]]}}))))))

  [:div
   (component-info table)
   (example "static table"
            (let [example1 {:id ::example1
                            :columns [{:key :first-name
                                       :title "First name"}
                                      {:key :last-name
                                       :title "Last name"}
                                      {:key :commands}]
                            :rows [::example-table-rows]
                            :default-sort-column :first-name}]
              [table example1]))
   (example "sortable and filterable table"
            (let [example2 {:id ::example2
                            :columns [{:key :first-name
                                       :title "First name"
                                       :sortable? true
                                       :filterable? true}
                                      {:key :last-name
                                       :title "Last name"
                                       :sortable? true
                                       :filterable? true}
                                      {:key :commands}]
                            :rows [::example-table-rows]
                            :default-sort-column :first-name}]
              [:div
               [search example2]
               [table example2]]))])
