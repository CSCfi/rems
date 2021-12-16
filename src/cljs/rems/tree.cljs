(ns rems.tree
  ;; TODO ns documentation
  (:require [clojure.string :as str]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [rems.atoms :refer [sort-symbol]]
            [rems.common.util :refer [conj-vec index-by]]
            [rems.guide-util :refer [component-info example namespace-info]]
            [rems.search :as search]))

;; TODO implement schema for the parameters

(defn apply-row-defaults [tree row]
  (let [children ((:children tree :children) row)]
    (merge
     ;; row defaults
     {:key ((:row-key tree :key) row)
      :children children
      :depth (:depth row 0)
      :value (dissoc row :depth)}

     ;; column defaults
     {:columns-by-key (->> (:columns tree)
                           (map-indexed (fn [i column]
                                          (let [first-column? (= i 0)
                                                value (if-let [value-fn (:value column (:key column))]
                                                        (value-fn row)
                                                        (get row (:key column)))
                                                display-value (str value)
                                                filter-value (if-let [filter-value-fn (:filter-value column)]
                                                               (filter-value-fn row)
                                                               (str/lower-case display-value))
                                                sort-value (if-let [sort-value-fn (:sort-value column)]
                                                             (sort-value-fn row)
                                                             (if (string? value)
                                                               (str/lower-case value)
                                                               value))]
                                            (merge {:sort-value sort-value
                                                    :display-value display-value
                                                    :filter-value filter-value
                                                    :td (when-let [content (if (:content column)
                                                                             ((:content column) row)
                                                                             [:div display-value])]
                                                          (if-let [td-fn (:td column)]
                                                            (td-fn row)

                                                            [:td {:class [(name (:key column))
                                                                          (str "bg-depth-" (:depth row 0))]
                                                                  :col-span (when-let [col-span-fn (:col-span column)] (col-span-fn row))}
                                                             [:div.d-flex.flex-row.w-100.align-items-baseline
                                                              {:class [(when first-column? (str "pad-depth-" (:depth row 0)))
                                                                       (when (:expanded? row) "expanded")]}

                                                              (when first-column?
                                                                (when (seq children)
                                                                  (if (:expanded? row)
                                                                    [:i.pl-1.pr-4.fas.fa-fw.fa-chevron-up]
                                                                    [:i.pl-1.pr-4.fas.fa-fw.fa-chevron-down])))

                                                              content]]))}
                                                   (dissoc column :td :col-span :sort-value :filter-value)))))
                           (index-by [:key]))}

     ;; copied over
     (select-keys row [:id :key :sort-value :display-value :filter-value :td :tr-class :parents :expanded?]))))

(defn sort-rows [sorting rows]
  (sort-by #(get-in % [:columns-by-key (:sort-column sorting) :sort-value])
           (case (:sort-order sorting)
             :desc #(compare %2 %1)
             #(compare %1 %2))
           rows))

(rf/reg-sub
 ::flattened-rows
 (fn [db [_ tree]]
   (let [rows @(rf/subscribe (:rows tree))
         expanded-rows @(rf/subscribe [::expanded-rows tree])
         sorting @(rf/subscribe [::sorting tree])
         filtering? (not (str/blank? (:filters @(rf/subscribe [::filtering tree]))))
         expand-row (fn [row]
                      (let [row-key ((:row-key tree :key) row)
                            expanded? (or filtering? ; must look at all rows
                                          (contains? expanded-rows row-key))]
                        (apply-row-defaults tree (assoc row :expanded? expanded?))))
         initial-rows (->> rows
                           (mapv expand-row)
                           (sort-rows sorting))]

     (loop [flattened []
            rows initial-rows]

       (if (empty? rows)
         flattened

         (let [row (first rows)]

           (if (:expanded? row)
             (let [child-depth (inc (:depth row))
                   child-parents (conj-vec (:parents row) (:key row))
                   new-rows (->> row
                                 :children
                                 (mapv #(assoc %
                                               :depth child-depth
                                               :parents child-parents))
                                 (mapv expand-row)
                                 (sort-rows sorting)
                                 vec)]

               (recur (into flattened [row])
                      (into new-rows (rest rows))))

             (recur (into flattened [row])
                    (rest rows)))))))))

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
 (fn [db [_ tree sort-column]]
   (update-in db [::sorting (:id tree)]
              (fn [sorting]
                (-> sorting
                    (assoc :sort-column sort-column)
                    (assoc :sort-order (change-sort-order (:sort-column sorting)
                                                          (:sort-order sorting)
                                                          sort-column)))))))

(rf/reg-sub
 ::sorting
 (fn [db [_ tree]]
   (or (get-in db [::sorting (:id tree)])
       {:sort-order (or (:default-sort-order tree)
                        :asc)
        :sort-column (or (:default-sort-column tree)
                         (-> tree :columns first :key))})))

(rf/reg-event-db
 ::set-filtering
 (fn [db [_ tree filtering]]
   (assoc-in db [::filtering (:id tree)] filtering)))

(rf/reg-sub
 ::filtering
 (fn [db [_ tree]]
   (get-in db [::filtering (:id tree)])))

(defn search
  "Search field component for filtering a `rems.tree/tree` instance
  which takes the same `tree` parameter as this component.

  See `rems.tree/Tree` for the `tree` parameter schema." ; TODO schema
  [tree]
  ;; (s/validate Tree tree)
  (let [filtering @(rf/subscribe [::filtering tree])
        on-search (fn [value]
                    (rf/dispatch [::set-filtering tree (assoc filtering :filters value)]))]
    [search/search-field {:id (str (name (:id tree)) "-search")
                          :on-search on-search
                          :searching? false}]))

(defn- sortable? [column]
  (:sortable? column true))

(defn- filterable? [column]
  (:filterable? column true))

(defn- display-row? [row filterable-columns search-terms]
  (or (empty? filterable-columns) ; tree has no filtering enabled
      (let [filterable-values (map (fn [column]
                                     (str (get-in row [:columns-by-key (:key column) :filter-value])))
                                   filterable-columns)]
        (every? (fn [search-term]
                  (some #(str/includes? % search-term) filterable-values))
                search-terms))))

(defn parse-search-terms [s]
  (->> (re-seq #"\S+" (str s))
       (map str/lower-case)))

(rf/reg-sub
 ::filtered-rows
 (fn [[_ tree] _]
   [(rf/subscribe [::flattened-rows tree])
    (rf/subscribe [::filtering tree])])
 (fn [[rows filtering] [_ tree]]
   (let [search-terms (parse-search-terms (:filters filtering))
         filterable-columns (filter filterable? (:columns tree))
         matching-rows (filter #(display-row? % filterable-columns search-terms) rows)
         matching-rows-set (set (mapv :key matching-rows))
         matching-rows-set (if (:show-matching-parents? tree)
                             (into matching-rows-set (mapcat :parents matching-rows))
                             matching-rows-set)]
     (mapv #(assoc % ::display-row? (contains? matching-rows-set (:key %))) ; performance optimization: hide DOM nodes instead of destroying them
           rows))))

(rf/reg-sub
 ::displayed-rows
 (fn [db [_ tree]]
   (let [rows @(rf/subscribe [::filtered-rows tree])
         rows (filter ::display-row? rows)]
     rows)))

(defn- set-toggle [set key]
  (let [set (or set #{})]
    (if (contains? set key)
      (disj set key)
      (conj set key))))

(rf/reg-event-db
 ::toggle-row-expanded
 (fn [db [_ tree key]]
   (let [new-db (update-in db [::expanded-rows (:id tree)] set-toggle key)]
     (when-let [on-expand (:on-expand tree)]
       (on-expand (get-in new-db [::expanded-rows (:id tree)])))
     new-db)))

(rf/reg-sub
 ::expanded-row
 (fn [db [_ tree key]]
   (contains? (get-in db [::expanded-rows (:id tree)]) key)))

(rf/reg-sub
 ::expanded-rows
 (fn [db [_ tree]]
   (get-in db [::expanded-rows (:id tree)])))

(rf/reg-event-db
 ::set-expanded-rows
 (fn [db [_ tree rows]]
   (let [expanded-rows (set (map :key rows))
         new-db (assoc-in db [::expanded-rows (:id tree)] expanded-rows)]
     (when-let [on-select (:on-select tree)]
       (on-select expanded-rows))
     new-db)))


(defn- tree-header [tree]
  (let [sorting @(rf/subscribe [::sorting tree])]
    (into [:tr #_(when (:selectable? tree) [selection-toggle-all tree])]
          (for [column (:columns tree)]
            [:th
             (when (sortable? column)
               {:class "pointer"
                :on-click #(rf/dispatch [::toggle-sorting tree (:key column)])})
             (:title column)
             " "
             (when (sortable? column)
               (when (= (:key column) (:sort-column sorting))
                 [sort-symbol (:sort-order sorting)]))]))))

(defn- tree-row [row tree]
  (into [:tr {:data-row (:key row)
              :class [(when-let [tr-class-fn (:tr-class tree)]
                        (tr-class-fn (:value row)))
                      (when (seq (:children row))
                        :clickable)]
              :on-click (when (seq (:children row))
                          #(rf/dispatch [::toggle-row-expanded tree (:key row)]))}]
        (for [column (:columns tree)]
          (:td (get (:columns-by-key row) (:key column))))))

(defn tree [tree]
  (let [rows @(rf/subscribe [::displayed-rows tree])
        language @(rf/subscribe [:language])]
    [:div.table-border ; TODO duplicate or generalize styles?
     [:table.rems-table {:id (name (:id tree))
                         :class (:id tree)}
      [:thead
       [tree-header tree]]
      [:tbody {:key language} ; performance optimization: rebuild instead of update existing components
       (for [row rows]
         ^{:key (:key row)}
         [tree-row row tree])]]]))



;;; guide

(def example-selected-rows (reagent/atom nil))

(defn guide []
  [:div
   (namespace-info rems.tree)
   (component-info tree)

   (example "empty tree"
            (rf/reg-sub ::empty-tree-rows (fn [_ _] []))

            [tree {:id ::example0
                   :columns [{:key :first-name
                              :title "First name"
                              :sortable? false
                              :filterable? false}
                             {:key :last-name
                              :title "Last name"
                              :sortable? false
                              :filterable? false}]
                   :rows [::empty-tree-rows]
                   :default-sort-column :first-name}])

   (example "setup example data"
            (defn- example-commands [text]
              [:div.commands.w-100 [:button.btn.btn-primary {:on-click #(do (js/alert (str "View " text)) (.stopPropagation %))} "View"]])

            (def example-data
              [{:key 0
                :name "Users"
                :children [{:key 1
                            :name "Applicants"
                            :commands (example-commands "Applicants")}
                           {:key 2
                            :name "Handlers"
                            :commands (example-commands "Handlers")}
                           {:key 3
                            :name "Administration"
                            :commands (example-commands "Administration")
                            :children [{:key 4
                                        :name "Reporters"
                                        :commands (example-commands "Reporters")}
                                       {:key 5
                                        :name "Owners"
                                        :commands (example-commands "Owners")
                                        :children [{:key 6
                                                    :name "Super Owners"
                                                    :commands (example-commands "Super owners")}
                                                   {:key 7
                                                    :name "Org Owners"
                                                    :commands (example-commands "Org Owners")}]}]}]
                :commands (example-commands "Users")}])

            (rf/reg-sub ::example-tree-rows (fn [_ _] example-data)))

   (example "static tree with a three level hierarchy"
            [tree {:id ::example1
                   :columns [{:key :name
                              :title "Name"
                              :sortable? false
                              :filterable? false}
                             {:key :commands
                              :content :commands
                              :sortable? false
                              :filterable? false}]
                   :rows [::example-tree-rows]
                   :default-sort-column :title}])

   ;; TODO implement selection if needed
   (example "sortable and filterable tree"
            [:p "Filtering and search can be added by using the " [:code "rems.tree/search"] " component"]
            (let [example2 {:id ::example2
                            :show-matching-parents? true
                            :columns [{:key :name
                                       :title "Name"}
                                      {:key :commands
                                       :content :commands
                                       :sortable? false
                                       :filterable? false}]
                            :rows [::example-tree-rows]
                            :default-sort-column :title}]
              [:div
               [search example2]
               [tree example2]]))])
