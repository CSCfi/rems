(ns rems.table
  "Sortable and filterable table component, which is optimized for
  continuous filtering between every key press - without lag.

  The implementation uses a non-conventional re-frame solution
  in order to support multiple components on the same page and
  to make filtering the table performant.

  - The `table` parameter is passed as a parameter to dynamic subscriptions,
    so that we can have multiple table components on the same page.
  - The rows are processed in stages by chaining subscriptions:
      (:rows table) -> [::rows table] -> [::sorted-rows table] -> [::sorted-and-filtered-rows table] -> [::displayed-rows table]
    This way only the last subscription, which does the filtering,
    needs to be recalculated when the user types the search parameters.
    Likewise, changing sorting only recalculates the last two subscriptions.

  (The users of this component don't need to know about these intermediate
  subscriptions and all other performance optimizations.)

  See `rems.tree/tree` for a similar tree component."
  (:require [clojure.string :as str]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [rems.atoms :refer [checkbox sort-symbol]]
            [rems.focus :as focus]
            [rems.guide-util :refer [component-info example namespace-info]]
            [rems.search :as search]
            [rems.text :refer [text text-format]]
            [schema.core :as s]))

(s/defschema ColumnKey
  s/Keyword)

(s/defschema Row
  {;; Unique key (within this table's rows) for React.
   :key s/Any
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

(s/defschema Table
  {;; Unique ID for this table, preferably a namespaced keyword.
   ;; Used not only as a HTML element ID, but also for separating
   ;; the internal state of different tables in the re-frame db.
   :id s/Keyword
   ;; The columns of the table, in the order that they should be shown.
   :columns [{;; Reference to a column in `rems.table/Row` of `:rows`.
              :key ColumnKey
              ;; Title to show at the top of the column.
              (s/optional-key :title) s/Str
              ;; Whether this column can be sorted.
              ;; Defaults to true.
              (s/optional-key :sortable?) s/Bool
              ;; Whether this column is used in filtering.
              ;; Defaults to true.
              (s/optional-key :filterable?) s/Bool

              ;; Whether to show this column, a function from all `rows` to a boolean
              (s/optional-key :when-rows) s/Any}]
   ;; The query vector for a re-frame subscription which returns
   ;; the data to show in this table. The subscription must return
   ;; data which confirms to the `rems.table/Rows` schema.
   :rows Subscription
   (s/optional-key :default-sort-column) (s/maybe ColumnKey)
   (s/optional-key :default-sort-order) (s/maybe (s/enum :asc :desc))
   ;; does the table have row selection?
   (s/optional-key :selectable?) s/Bool
   ;; callback for currently selected row keys
   (s/optional-key :on-select) (s/=> [ColumnKey])})


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
                         (-> table :columns first :key))}))) ; NB: first column is possibly not visible

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

(defn- assoc-if-missing [m k make-default]
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
                                             [:td {:class (name column)}
                                              (:display-value opts)]))))]))
       (into {})))

(rf/reg-sub
 ::columns
 (fn [_ [_ table]]
   (:columns table)))

(rf/reg-sub
 ::filtered-columns
 (fn [[_ table] _]
   [(rf/subscribe [::columns table])
    (rf/subscribe [::rows table])]) ; use original rows so columns don't come and go based on filtering
 (fn [[columns rows] _]
   (for [column columns
         :when ((:when-rows column identity) rows)]
     column)))

(rf/reg-sub
 ::rows
 (fn [[_ table] _]
   [(rf/subscribe (:rows table))])
 (fn [[rows] _]
   (->> rows
        (s/validate Rows)
        (map apply-row-defaults)
        ;; TODO: this second validation could be done with a schema where s/optional-key is not used
        ;;       (or then we could just not validate these internal row representations)
        (s/validate Rows))))

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
    (rf/subscribe [::filtering table])
    (rf/subscribe [::filtered-columns table])])
 (fn [[rows filtering columns] [_ _table]]
   (let [search-terms (parse-search-terms (:filters filtering))
         columns (->> columns
                      (filter filterable?))]
     (->> rows
          (map (fn [row]
                 ;; performance optimization: hide DOM nodes instead of destroying them
                 (assoc row ::display-row? (display-row? row columns search-terms))))))))

(rf/reg-sub
 ::displayed-rows
 (fn [db [_ table]]
   (let [rows @(rf/subscribe [::sorted-and-filtered-rows table])
         max-rows @(rf/subscribe [::max-rows table])
         rows (filter ::display-row? rows)]
     (take max-rows rows))))

(defn search
  "Search field component for filtering a `rems.table/table` instance
  which takes the same `table` parameter as this component.

  See `rems.table/Table` for the `table` parameter schema."
  [table]
  (s/validate Table table)
  (let [filtering @(rf/subscribe [::filtering table])
        on-search (fn [value]
                    (rf/dispatch [::set-filtering table (assoc filtering :filters value)]))]
    [search/search-field {:id (str (name (:id table)) "-search")
                          :on-search on-search
                          :searching? false}]))

(defn- set-toggle [set key]
  (let [set (or set #{})]
    (if (contains? set key)
      (disj set key)
      (conj set key))))

(rf/reg-event-db
 ::toggle-row-selection
 (fn [db [_ table key]]
   (let [new-db (update-in db [::selected-rows (:id table)] set-toggle key)]
     (when-let [on-select (:on-select table)]
       (on-select (get-in new-db [::selected-rows (:id table)])))
     new-db)))

(rf/reg-sub
 ::selected-row
 (fn [db [_ table key]]
   (contains? (get-in db [::selected-rows (:id table)]) key)))

(rf/reg-sub
 ::selected-rows
 (fn [db [_ table]]
   (get-in db [::selected-rows (:id table)])))

(rf/reg-event-db
 ::set-selected-rows
 (fn [db [_ table rows]]
   (let [selected-rows (set (map :key rows))
         new-db (assoc-in db [::selected-rows (:id table)] selected-rows)]
     (when-let [on-select (:on-select table)]
       (on-select selected-rows))
     new-db)))

(rf/reg-sub
 ::row-selection-state
 (fn [db [_ table]]
   (let [selected-rows @(rf/subscribe [::selected-rows table])
         all-visible-rows (set (map :key @(rf/subscribe [::displayed-rows table])))
         all-selected?  (and (= all-visible-rows selected-rows) (seq all-visible-rows))
         some-selected? (seq selected-rows)]
     (cond all-selected? :all
           some-selected? :some
           :else :none))))

(defn- selection-toggle-all
  "A checkbox-like component useful for a selection toggle in the table header."
  [table]
  (let [selection-state @(rf/subscribe [::row-selection-state table])
        visible-rows @(rf/subscribe [::displayed-rows table])
        on-change (case selection-state
                    :all #(rf/dispatch [::set-selected-rows table []])
                    :some #(rf/dispatch [::set-selected-rows table []])
                    :none #(rf/dispatch [::set-selected-rows table visible-rows]))]
    [:th.selection
     [:i.far.fa-lg.pointer
      {:id (str (:id table) "-selection-toggle-all")
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

(defn- table-header [table]
  (let [sorting @(rf/subscribe [::sorting table])
        columns @(rf/subscribe [::filtered-columns table])]
    (into [:tr (when (:selectable? table) [selection-toggle-all table])]
          (for [column columns]
            [:th
             (when (sortable? column)
               {:class "pointer"
                :on-click #(rf/dispatch [::toggle-sorting table (:key column)])})
             (:title column)
             " "
             (when (sortable? column)
               (when (= (:key column) (:sort-column sorting))
                 [sort-symbol (:sort-order sorting)]))]))))

(defn- table-row [row table columns]
  (into [:tr {:data-row (:key row)
              :class (when (:selectable? table)
                       [:clickable
                        (when @(rf/subscribe [::selected-row table (:key row)]) :selected)])
              ;; performance optimization: hide DOM nodes instead of destroying them
              :style {:display (if (::display-row? row)
                                 "table-row"
                                 "none")}
              :on-click (when (:selectable? table)
                          #(when (contains? #{"TR" "TD" "TH"} (.. % -target -tagName)) ; selection is the default action
                             (rf/dispatch [::toggle-row-selection table (:key row)])))}
         (when (:selectable? table)
           [:td.selection
            [checkbox {:value @(rf/subscribe [::selected-row table (:key row)])
                       :on-change #(rf/dispatch [::toggle-row-selection table (:key row)])}]])]
        (for [column columns]
          (let [cell (get row (:key column))]
            (assert cell {:error "the row is missing a column"
                          :column (:key column)
                          :row row})
            (:td cell)))))

(defn table
  "A filterable and sortable table component.
  Meant to be used together with the `rems.table/search` component.

  See `rems.table/Table` for the `table` parameter schema."
  [table]
  (s/validate Table table)
  (let [rows @(rf/subscribe [::sorted-and-filtered-rows table]) ; TODO refactor to use ::displayed-rows
        language @(rf/subscribe [:language])
        max-rows @(rf/subscribe [::max-rows table])
        columns @(rf/subscribe [::filtered-columns table])
        ;; When showing all rows, table-row is responsible for filtering displayed rows,
        ;; but with truncation the visible rows need to be filtered before truncation.
        rows (if (< max-rows (count rows))
               (filter ::display-row? rows)
               rows)]
    [:div.table-border
     [:table.rems-table {:id (name (:id table))
                         :class (:id table)}
      [:thead
       [table-header table]]
      [:tbody {:key language} ; performance optimization: rebuild instead of update existing components
       (for [row (take max-rows rows)]
         ^{:key (:key row)} [table-row row table columns])]
      (when (< max-rows (count rows))
        [:tfoot
         [:tr [:td {:col-span (+ (count columns)
                                 (if (:selectable? table) 1 0))
                    :style {:text-align :center}}
               [:button.btn.btn-primary {:type :button
                                         :on-click (fn []
                                                     (rf/dispatch [::show-all-rows table])
                                                     (let [next-row (:key (nth rows max-rows))]
                                                       (focus/focus-element-async (str "table.rems-table." (name (:id table))
                                                                                       " > tbody > tr[data-row='" next-row "'] > td"))))}
                (text-format :t.table/show-all-n-rows (count rows))]]]])]]))

;;; guide

(def example-selected-rows (reagent/atom nil))

(defn guide []
  [:div
   (namespace-info rems.table)
   (component-info table)

   (example "empty table"
            (rf/reg-sub ::empty-table-rows (fn [_ _] []))

            [table {:id ::example0
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

   (example "static table with three rows"
            (defn- example-commands [text]
              {:td [:td.commands [:button.btn.btn-primary {:on-click #(do (js/alert (str "View " text)) (.stopPropagation %))} "View"]]})

            (def example-data
              [{:key 1
                :first-name {:value "Cody"}
                :last-name {:value "Turner"}
                :commands (example-commands "Cody")}
               {:key 2
                :first-name {:value "Melanie"}
                :last-name {:value "Palmer"}
                :commands (example-commands "Melanie")}
               {:key 3
                :first-name {:value "Henry"}
                :last-name {:value "Herring"}
                :commands (example-commands "Henry")}])

            (rf/reg-sub ::example-table-rows (fn [_ _] example-data))

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

   (example "table with selectable rows"
            [:p "The table components supports selection of rows. You can provide a callback for when the set of selected rows changes."]

            [:div [:p "You have " (count @example-selected-rows) " rows selected."]]
            [table {:id ::example-selectable
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
                    :rows [::example-table-rows]
                    :default-sort-column :first-name
                    :selectable? true
                    :on-select #(reset! example-selected-rows %)}])

   (example "sortable and filterable table"
            [:p "Filtering and search can be added by using the " [:code "rems.table/search"] " component"]
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
                                      {:key :not-shown
                                       :title "Not shown"
                                       :when-rows (constantly false)}
                                      {:key :points
                                       :title "Points"}]
                            :rows [::example-rich-table-rows]}]
              [:div
               [search example3]
               [table example3]]))])
