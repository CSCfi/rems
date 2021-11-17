(ns rems.tree
  "Sortable and filterable tree component that looks like a table,
  which is optimized for continuous filtering between
  every key press - without lag.

  The implementation uses a non-conventional re-frame solution
  in order to support multiple components on the same page and
  to make filtering the tree performant.

  - The `tree` parameter is passed as a parameter to dynamic subscriptions,
    so that we can have multiple tree components on the same page.
  - The rows are processed in stages by chaining subscriptions:
      (:rows tree) -> [::rows tree] -> [::sorted-rows tree] -> [::sorted-and-filtered-rows tree] -> [::displayed-rows tree]
    This way only the last subscription, which does the filtering,
    needs to be recalculated when the user types the search parameters.
    Likewise, changing sorting only recalculates the last two subscriptions.

  (The users of this component don't need to know about these intermediate
  subscriptions and all other performance optimizations.)"
  (:require [clojure.string :as str]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [rems.atoms :refer [checkbox sort-symbol]]
            [rems.guide-util :refer [component-info example namespace-info]]
            [rems.search :as search]
            [rems.text :refer [text]]
            [schema.core :as s]))

(s/defschema ColumnKey
  s/Keyword)

(s/defschema Row
  {;; Unique key (within this tree's rows) for React.
   :key s/Any
   (s/optional-key :children) [Row] ; if this row / tree node has children
   (s/optional-key :depth) s/Int ; how deep we are in the tree
   ;; Data to show in the current row, organized by column.
   ColumnKey {;; The value to show in the current row and column.
              ;; Not used directly, but serves as a default for the
              ;; following keys to simplify the common cases.
              (s/optional-key :value) s/Any
              ;; Comparable value for use in sorting.
              ;; The default is derived from :value.
              (s/optional-key :sort-value) s/Any
              ;; Lowercase string for use in filtering.
              ;; The default is derived from :value.
              (s/optional-key :filter-value) s/Str
              ;; Simple value for rendering in the UI as-is.
              ;; The default is derived from :value.
              (s/optional-key :display-value) s/Any
              ;; <td> element in hiccup format for rendering in the UI.
              ;; The default is derived from :display-value.
              (s/optional-key :td) [(s/one s/Keyword ":td hiccup element")
                                    s/Any]}})

(s/defschema Rows
  [Row])

(s/defschema Subscription
  [(s/one s/Keyword "query-id")
   s/Any])

(s/defschema Tree
  {;; Unique ID for this tree, preferably a namespaced keyword.
   ;; Used not only as a HTML element ID, but also for separating
   ;; the internal state of different tables in the re-frame db.
   :id s/Keyword
   ;; The columns of the tree, in the order that they should be shown.
   :columns [{;; Reference to a column in `rems.table/Row` of `:rows`.
              :key ColumnKey
              ;; Title to show at the top of the column.
              (s/optional-key :title) s/Str
              ;; Whether this column can be sorted.
              ;; Defaults to true.
              (s/optional-key :sortable?) s/Bool
              ;; Whether this column is used in filtering.
              ;; Defaults to true.
              (s/optional-key :filterable?) s/Bool}]
   ;; The query vector for a re-frame subscription which returns
   ;; the data to show in this table. The subscription must return
   ;; data which confirms to the `rems.tree/Rows` schema.
   :rows Subscription
   (s/optional-key :default-sort-column) (s/maybe ColumnKey)
   (s/optional-key :default-sort-order) (s/maybe (s/enum :asc :desc))
   ;; does the tree have row selection?
   (s/optional-key :selectable?) s/Bool
   ;; callback for currently selected row keys
   (s/optional-key :on-select) (s/=> [ColumnKey])})


(rf/reg-event-db
 ::reset
 (fn [db _]
   (dissoc db ::sorting ::filtering)))

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

(defn- assoc-if-missing [m k make-default]
  (if (contains? m k)
    m
    (assoc m k (make-default m))))

(defn apply-row-defaults [row expanded]
  (->> row
       (map (fn [[column opts]]
              [column
               (if (contains? #{:key :children :depth :expanded} column) ; not a column
                 opts
                 (-> opts
                     (assoc-if-missing :sort-value (fn [opts]
                                                     (let [val (:value opts)]
                                                       (if (string? val)
                                                         (str/lower-case val)
                                                         val))))
                     (assoc-if-missing :display-value (comp str :value))
                     (assoc-if-missing :filter-value (fn [opts]
                                                       (if (string? (:display-value opts))
                                                         (str/lower-case (:display-value opts))
                                                         "")))
                     (assoc-if-missing :td (fn [opts]
                                             [:td {:class [(name column)
                                                           (str "depth-" (:depth row 0))]}
                                              [:div {:class (when expanded "expanded")}
                                               (if (seq (:children row))
                                                 (if expanded
                                                   [:i.pr-1.fas.fa-md.fa-chevron-down]
                                                   [:i.pr-1.fas.fa-md.fa-chevron-up])
                                                 [:i.pr-1.fas.fa-md]) ; spacing
                                               (:display-value opts)]]))))]))
       (into {})))

(rf/reg-sub
 ::rows
 (fn [[_ tree] _]
   [(rf/subscribe (:rows tree))])
 (fn [[rows] _]
   (->> rows
        #_(s/validate Rows)
        #_(map apply-row-defaults)
        ;; TODO: this second validation could be done with a schema where s/optional-key is not used
        ;;       (or then we could just not validate these internal row representations)
        #_(s/validate Rows))))

(rf/reg-sub
 ::flattened-rows
 (fn [[_ tree] _]
   [(rf/subscribe [::rows tree])
    (rf/subscribe [::expanded-rows tree])])
 (fn [[rows expanded-rows] _]
   (loop [flattened []
          rows rows]
     (if (empty? rows)
       (map #(apply-row-defaults % (contains? expanded-rows (:key %))) flattened)
       (let [row (first rows)
             depth (:depth row 0)
             expanded (contains? expanded-rows (:key row))
             new-depth (inc depth)
             children (mapv #(assoc % :depth new-depth) (:children row))]
         (prn :row row)
         (if (and expanded (seq children))
           (recur (into flattened [row])
                  (into (vec children) (rest rows)))
           (recur (into flattened [row])
                  (rest rows))))))))

(rf/reg-sub
 ::sorted-rows
 (fn [[_ tree] _]
   [(rf/subscribe [::flattened-rows tree])
    (rf/subscribe [::sorting tree])])
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
  (or (empty? filtered-columns) ; tree has no filtering enabled
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
 (fn [[_ tree] _]
   [(rf/subscribe [::sorted-rows tree])
    (rf/subscribe [::filtering tree])])
 (fn [[rows filtering] [_ tree]]
   (let [search-terms (parse-search-terms (:filters filtering))
         columns (->> (:columns tree)
                      (filter filterable?))]
     (->> rows
          (map (fn [row]
                 ;; performance optimization: hide DOM nodes instead of destroying them
                 (assoc row ::display-row? (display-row? row columns search-terms))))))))

(rf/reg-sub
 ::displayed-rows
 (fn [db [_ tree]]
   (let [rows @(rf/subscribe [::sorted-and-filtered-rows tree])
         rows (filter ::display-row? rows)]
     rows)))

(defn search
  "Search field component for filtering a `rems.table/table` instance
  which takes the same `tree` parameter as this component.

  See `rems.table/Table` for the `tree` parameter schema."
  [tree]
  (s/validate Tree tree)
  (let [filtering @(rf/subscribe [::filtering tree])
        on-search (fn [value]
                    (rf/dispatch [::set-filtering tree (assoc filtering :filters value)]))]
    [search/search-field {:id (str (name (:id tree)) "-search")
                          :on-search on-search
                          :searching? false}]))

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

(rf/reg-event-db
 ::toggle-row-selection
 (fn [db [_ tree key]]
   (let [new-db (update-in db [::selected-rows (:id tree)] set-toggle key)]
     (when-let [on-select (:on-select tree)]
       (on-select (get-in new-db [::selected-rows (:id tree)])))
     new-db)))

(rf/reg-sub
 ::selected-row
 (fn [db [_ tree key]]
   (contains? (get-in db [::selected-rows (:id tree)]) key)))

(rf/reg-sub
 ::selected-rows
 (fn [db [_ tree]]
   (get-in db [::selected-rows (:id tree)])))

(rf/reg-event-db
 ::set-selected-rows
 (fn [db [_ tree rows]]
   (let [selected-rows (set (map :key rows))
         new-db (assoc-in db [::selected-rows (:id tree)] selected-rows)]
     (when-let [on-select (:on-select tree)]
       (on-select selected-rows))
     new-db)))

(rf/reg-sub
 ::row-selection-state
 (fn [db [_ tree]]
   (let [selected-rows @(rf/subscribe [::selected-rows tree])
         all-visible-rows (set (map :key @(rf/subscribe [::displayed-rows tree])))
         all-selected?  (and (= all-visible-rows selected-rows) (seq all-visible-rows))
         some-selected? (seq selected-rows)]
     (cond all-selected? :all
           some-selected? :some
           :else :none))))

(defn- selection-toggle-all
  "A checkbox-like component useful for a selection toggle in the tree header."
  [tree]
  (let [selection-state @(rf/subscribe [::row-selection-state tree])
        visible-rows @(rf/subscribe [::displayed-rows tree])
        on-change (case selection-state
                    :all #(rf/dispatch [::set-selected-rows tree []])
                    :some #(rf/dispatch [::set-selected-rows tree []])
                    :none #(rf/dispatch [::set-selected-rows tree visible-rows]))]
    [:th.selection
     [:i.far.fa-lg.pointer
      {:id (str (:id tree) "-selection-toggle-all")
       :class (case selection-state
                :all :fa-check-square
                :some :fa-minus-square
                :none :fa-square)
       :tabIndex 0
       :role :checkbox
       :aria-checked (case selection-state
                       :all true
                       :some :mixed
                       :none false)
       :aria-label (case selection-state
                     :all (text :t.table/all-rows-selected)
                     :some (text :t.table/some-rows-selected)
                     :none (text :t.table/no-rows-selected))
       :on-click on-change
       :on-key-press #(when (= (.-key %) " ")
                        (on-change))}]]))

(defn- table-header [tree]
  (let [sorting @(rf/subscribe [::sorting tree])]
    (into [:tr (when (:selectable? tree) [selection-toggle-all tree])]
          (for [column (:columns tree)]
            [:th
             (when (sortable? column)
               {:on-click #(rf/dispatch [::toggle-sorting tree (:key column)])})
             (:title column)
             " "
             (when (sortable? column)
               (when (= (:key column) (:sort-column sorting))
                 [sort-symbol (:sort-order sorting)]))]))))

(defn- table-row [row tree]
  (into [:tr {:data-row (:key row)
              :class (when (or (:selectable? tree)
                               (seq (:children row)))
                       [:clickable
                        (when @(rf/subscribe [::selected-row tree (:key row)]) :selected)])
              ;; performance optimization: hide DOM nodes instead of destroying them
              :style {:display (if (::display-row? row)
                                 "table-row"
                                 "none")}
              :on-click (cond (seq (:children row))
                              #(do (prn row) (rf/dispatch [::toggle-row-expanded tree (:key row)]))

                              (:selectable? tree)
                              #(when (contains? #{"TR" "TD" "TH"} (.. % -target -tagName)) ; selection is the default action
                                 (rf/dispatch [::toggle-row-selection tree (:key row)])))}
         (when (:selectable? tree)
           [:td.selection
            [checkbox {:value @(rf/subscribe [::selected-row tree (:key row)])
                       :on-change #(rf/dispatch [::toggle-row-selection tree (:key row)])}]])]
        (for [column (:columns tree)]
          (let [cell (get row (:key column))]
            (assert cell {:error "the row is missing a column"
                          :column (:key column)
                          :row row})
            (:td cell)))))

(defn tree
  "A filterable and sortable tree component.
  Can be used together with the `rems.table/search` component.

  See `rems.tree/Table` for the `tree` parameter schema."
  [tree]
  (s/validate Tree tree)
  (let [rows @(rf/subscribe [::displayed-rows tree])
        language @(rf/subscribe [:language])]
    [:div.table-border
     [:table.rems-table {:id (name (:id tree))
                         :class (:id tree)}
      [:thead
       [table-header tree]]
      [:tbody {:key language} ; performance optimization: rebuild instead of update existing components
       (for [row rows]
         ^{:key (:key row)}
         [table-row row tree])]]]))



;;; guide

(def example-selected-rows (reagent/atom nil))

(defn guide []
  [:div
   (namespace-info rems.tree)
   (component-info tree)

   (example "empty tree"
            (rf/reg-sub ::empty-table-rows (fn [_ _] []))

            [tree {:id ::example0
                   :columns [{:key :first-name
                              :title "First name"
                              :sortable? false
                              :filterable? false}
                             {:key :last-name
                              :title "Last name"
                              :sortable? false
                              :filterable? false}]
                   :rows [::empty-table-rows]
                   :default-sort-column :first-name}])

   (example "setup example data"
            (defn- example-commands [text]
              {:td [:td.commands [:button.btn.btn-primary {:on-click #(do (js/alert (str "View " text)) (.stopPropagation %))} "View"]]})

            (def example-data
              [{:key 0
                :category {:value "Users"}
                :children [{:key 1
                            :category {:value "Applicants"}
                            :commands (example-commands "Applicants")}
                           {:key 2
                            :category {:value "Handlers"}
                            :commands (example-commands "Handlers")}
                           {:key 3
                            :category {:value "Administration"}
                            :commands (example-commands "Administration")
                            :children [{:key 4
                                        :category {:value "Reporters"}
                                        :commands (example-commands "Reporters")}
                                       {:key 5
                                        :category {:value "Owners"}
                                        :commands (example-commands "Owners")
                                        :children [{:key 6
                                                    :category {:value "Super Owners"}
                                                    :commands (example-commands "Super owners")}
                                                   {:key 7
                                                    :category {:value "Org Owners"}
                                                    :commands (example-commands "Org Owners")}]}]}]
                :commands (example-commands "Handlers")}])

            (rf/reg-sub ::example-tree-rows (fn [_ _] example-data)))

   (example "static tree with three rows"
            [tree {:id ::example1
                   :columns [{:key :category
                              :title "Name"
                              :sortable? false
                              :filterable? false}
                             {:key :commands
                              :sortable? false
                              :filterable? false}]
                   :rows [::example-tree-rows]
                   :default-sort-column :name}])
   #_(example "tree with selectable rows"
              [:p "The tree components supports selection of rows. You can provide a callback for when the set of selected rows changes."]
              [:div [:p "You have " (count @example-selected-rows) " rows selected."]]
              [tree {:id ::example-selectable
                     :columns [{:key :first-name
                                :title "First name"
                                :sortable? false
                                :filterable? false}
                               {:key :last-name
                                :title "Last name"
                                :sortable? false
                                :filterable? false}
                               {:key :commands
                                :sortable? false
                                :filterable? false}]
                     :rows [::example-tree-rows]
                     :default-sort-column :first-name
                     :selectable? true
                     :on-select #(reset! example-selected-rows %)}])
   #_(example "sortable and filterable tree"
              [:p "Filtering and search can be added by using the " [:code "rems.table/search"] " component"]
              (let [example2 {:id ::example2
                              :columns [{:key :first-name
                                         :title "First name"}
                                        {:key :last-name
                                         :title "Last name"}
                                        {:key :commands
                                         :sortable? false
                                         :filterable? false}]
                              :rows [::example-tree-rows]
                              :default-sort-column :first-name}]
                [:div
                 [search example2]
                 [tree example2]]))
   #_(example "richer example data"
              [:p "Hawks have a special sort-value so they are always listed first (or last if order is flipped)."
               "Also, filtering ignores the word \"Team\"."
               "Also, the score has special styling."
               "Eagles have special styling. :value is used for sorting & filtering but :td for rendering."]
              (def example-data-rich
                [{:key 1
                  :team {:display-value "Team Hawks"
                         :filter-value "hawks"
                         :sort-value "0000hawks"}
                  :points {:value 3
                           :display-value "-> 3 <-"}}
                 {:key 2
                  :team {:value "Eagles"
                         :td [:td.eagles-are-best [:em "Eagles"]]}
                  :points {:value 4}}
                 {:key 3
                  :team {:value "Ravens"}
                  :points {:value 0}}])

              (rf/reg-sub ::example-rich-table-rows (fn [_ _] example-data-rich))
              [:p "Now the data can be used like so"]
              (let [example3 {:id ::example3
                              :columns [{:key :team
                                         :title "Team"}
                                        {:key :points
                                         :title "Points"}]
                              :rows [::example-rich-table-rows]}]
                [:div
                 [search example3]
                 [tree example3]]))])
