(ns rems.tree
  "Sortable and filterable table component.

  The implementation uses a non-conventional re-frame solution
  in order to support multiple components on the same page and
  to make filtering the table performant.

  The tree data structure is flattened to a list of rows with depths
  and `:parents` - `:children` relationships.

  - The `tree` parameter is passed as a parameter to dynamic subscriptions,
    so that we can have multiple table components on the same page.
  - The rows are processed in stages by chaining subscriptions:
      (:rows tree) -> [::flattened-rows tree] -> [::filtered-rows table] -> [::displayed-rows tree]
    This way only the last subscription, which does the filtering,
    needs to be recalculated when the user types the search parameters.
    Likewise, changing filtering only recalculates the last two subscriptions.

  (The users of this component don't need to know about these intermediate
  subscriptions and all other performance optimizations.)

  See `rems.table/table` for a similar table component."
  (:require [clojure.string :as str]
            [goog.functions :refer [debounce]]
            [re-frame.core :as rf]
            [rems.atoms :refer [sort-symbol]]
            [rems.common.util :refer [conj-vec index-by]]
            [rems.guide-util :refer [component-info example namespace-info]]
            [rems.search :as search]
            [schema.core :as s]))

(s/defschema ColumnKey
  s/Keyword)

(s/defschema Row
  {;; Unique key for the row.
   :key s/Any
   ;; Unique key (within this trees's rows) for React.
   :react-key s/Str
   ;; The children of this row (it's a tree after all).
   :children [s/Any]
   ;; Depth of this row (the tree is flattened to rows).
   :depth s/Int
   ;; Value of the row.
   :value s/Any

   ;; Data to show in the current row, organized by column.
   :columns-by-key {ColumnKey {;; Column key (keyword)
                               :key s/Keyword
                               ;; Value used in HTML.
                               :display-value s/Any
                               ;; Value used for sorting.
                               :sort-value s/Any
                               ;; Value used for filtering.
                               ;; The default is derived from display-value.
                               :filter-value s/Str
                               ;; <td> element in hiccup format for rendering in the UI.
                               :td (s/maybe [(s/one s/Keyword ":td hiccup element") s/Any])}}
   ;; The keys of the parents of this row (populated automatically).
   (s/optional-key :parents) [s/Any]
   ;; Whether to hide or show this row (populated automatically).
   (s/optional-key ::display-row?) s/Bool
   ;; Whether this element is opened and visible (populated automatically).
   :expanded? s/Bool})

(s/defschema Rows
  [Row])

(s/defschema Subscription
  [(s/one s/Keyword "query-id")
   s/Any])

;; NB: The tree is flattened to a table of rows.
(s/defschema Tree
  {;; Unique ID for this tree, preferably a namespaced keyword.
   ;; Used not only as a HTML element ID, but also for separating
   ;; the internal state of different trees in the re-frame db.
   :id s/Keyword
   ;; The columns of the table, in the order that they should be shown.
   ;; Optional function (Row -> key value) for the React key value
   ;; The default is from :key.
   (s/optional-key :row-key) s/Any
   :columns [{;; Reference to a column in `rems.tree/Row` of `:rows`.
              :key ColumnKey
              ;; Title to show at the top of the column.
              (s/optional-key :title) s/Str
              ;; ARIA label title to show at the top of the column.
              ;; Defaults to `:title`.
              (s/optional-key :aria-label) s/Str
              ;; Optional function (row -> value) to calculate the value to show
              ;; Defaults to the (presumable keyword) value of the `:key` prop.
              (s/optional-key :value) s/Any
              ;; Optional function (row -> display value) returning the value used for in HTML.
              ;; The default is derived from :value.
              (s/optional-key :display-value) s/Any
              ;; Optional function (row -> sort value) returning the value used for sorting.
              ;; The default is derived from :display-value.
              (s/optional-key :sort-value) s/Any
              ;; Optional function (row -> filter value) returning the value used for filtering.
              ;; The default is derived from :display-value
              (s/optional-key :filter-value) s/Str
              ;; How many columns does this particular column fill?
              ;; Optional function (row -> span). Defaults to 1.
              (s/optional-key :col-span) s/Any
              ;; Optional function (row -> content) to calculate the content to show
              ;; inside the cell's <td> element. Sometimes it is preferable to override
              ;; the whole td with `:td`.
              (s/optional-key :content) s/Any
              ;; Optional function (row -> <td>) to calculate the
              ;; <td> element in hiccup format for rendering in the UI.
              ;; Defining this function will override the previous display related functions.
              ;; The default is derived from :display-value.
              (s/optional-key :td) s/Any
              ;; Whether this column can be sorted.
              ;; Defaults to true.
              (s/optional-key :sortable?) s/Bool
              ;; Whether this column is used in filtering.
              ;; Defaults to true.
              (s/optional-key :filterable?) s/Bool}]
   ;; The query vector for a re-frame subscription which returns
   ;; the data to show in this table. The subscription must return
   ;; data which confirms to the `rems.tree/Rows` schema.
   ;; Only the top-level of the tree should be returned by the subscription. (see `:children`)
   :rows Subscription
   ;; A function (row -> child rows) which returns the children
   ;; of the given row.
   ;; Defaults to `:children`.
   (s/optional-key :children) s/Any
   ;; An optional function (row -> boolean) for filtering
   ;; rows from the tree after it has been flattened.
   ;; Return true for the rows you want to be included.
   ;; Defaults to no filtering.
   (s/optional-key :row-filter) s/Any
   ;; When filtering, should the parent nodes of a matching node
   ;; always be shown, or only the matching row?
   (s/optional-key :show-matching-parents?) s/Bool
   ;; Optional function (value -> class) to give specific classes to rows (tr elements)
   (s/optional-key :tr-class) s/Any
   ;; Default sorting column and order.
   (s/optional-key :default-sort-column) (s/maybe ColumnKey)
   (s/optional-key :default-sort-order) (s/maybe (s/enum :asc :desc))})

(defn apply-row-defaults [tree row]
  (let [children ((:children tree :children) row)
        row-key ((:row-key tree :key) row)]
    (merge
     ;; row defaults
     {:key row-key
      :react-key (str (str/join "_" (:parents row)) "_" row-key)
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
                                                display-value (if-let [display-value-fn (:display-value column)]
                                                                (display-value-fn row)
                                                                (str value))
                                                filter-value (if-let [filter-value-fn (:filter-value column)]
                                                               (filter-value-fn row)
                                                               (str/lower-case display-value))
                                                sort-value (if-let [sort-value-fn (:sort-value column)]
                                                             (sort-value-fn row)
                                                             (if (string? value)
                                                               (str/lower-case value)
                                                               value))]
                                            {:key (:key column)
                                             :sort-value sort-value
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
                                                      (if first-column? ; wrap open chevron?
                                                        [:div.d-flex.flex-row.w-100.align-items-baseline
                                                         {:class [(str "pad-depth-" (:depth row 0))
                                                                  (when (:expanded? row) "expanded")]}

                                                         (when (seq children)
                                                           (if (:expanded? row)
                                                             [:i.pl-1.pr-4.fas.fa-fw.fa-chevron-up]
                                                             [:i.pl-1.pr-4.fas.fa-fw.fa-chevron-down]))

                                                         content]

                                                        content)]))})))
                           (index-by [:key]))}

     ;; copied over
     (select-keys row [:key :tr-class :parents :expanded?]))))

(defn sort-rows [sorting rows]
  (sort-by #(get-in % [:columns-by-key (:sort-column sorting) :sort-value])
           (case (:sort-order sorting)
             :desc #(compare %2 %1)
             #(compare %1 %2))
           rows))

(defn- extra-row-filtering [extra-filter rows]
  (if extra-filter
    (filter extra-filter rows)
    rows))

(rf/reg-sub
 ::flattened-rows
 (fn [db [_ tree]]
   (let [rows @(rf/subscribe (:rows tree))
         expanded-rows @(rf/subscribe [::expanded-rows tree])
         sorting @(rf/subscribe [::sorting tree])
         filtering? (not (str/blank? (:filters @(rf/subscribe [::filtering tree]))))
         apply-defaults (fn [row]
                          (let [row-key ((:row-key tree :key) row)
                                expanded? (or filtering? ; must look at all rows
                                              (contains? expanded-rows row-key))]
                            (apply-row-defaults tree (assoc row :expanded? expanded?))))
         initial-rows (->> rows
                           (extra-row-filtering (:row-filter tree))
                           (mapv apply-defaults)
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
                                 (extra-row-filtering (:row-filter tree))
                                 (mapv #(assoc %
                                               :depth child-depth
                                               :parents child-parents))
                                 (mapv apply-defaults)
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
  (s/validate Tree tree)
  (let [filtering @(rf/subscribe [::filtering tree])
        on-search (debounce (fn [value]
                              (rf/dispatch [::set-filtering tree (assoc filtering :filters value)]))
                            500)]
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
 (fn [_db [_ tree]]
   (let [rows @(rf/subscribe [::filtered-rows tree])
         rows (filter ::display-row? rows)]
     (s/validate Rows rows))))

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
     new-db)))


(defn- tree-header [tree]
  (let [sorting @(rf/subscribe [::sorting tree])]
    (into [:tr]
          (for [column (:columns tree)]
            [:th
             (when (sortable? column)
               {:class "pointer"
                :on-click #(rf/dispatch [::toggle-sorting tree (:key column)])})
             (or (:title column)
                 [:span.sr-only (:aria-label column)])
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
         ^{:key (:react-key row)} ; row key can be duplicated because it's a DAG
         [tree-row row tree])]]]))



;;; guide

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

   (example "static tree with a three level hierarchy"
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

            (rf/reg-sub ::example-tree-rows (fn [_ _] example-data))

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
