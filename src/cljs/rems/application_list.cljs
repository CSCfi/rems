(ns rems.application-list
  (:require [clojure.string :as str]
            [rems.atoms :refer [sort-symbol]]
            [rems.text :refer [localize-state localize-time text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- view-button [app]
  [:a.btn.btn-primary
   {:href (str "#/application/" (:id app))}
   (text :t.applications/view)])

(defn- get-catalogue-items [app]
  (str/join ", " (map :title (:catalogue-items app))))

(def +all-columns+
  [:id :resource :applicant :state :created :handled])

(def +default-columns+
  [:id :resource :applicant :state :created])

(def ^:private +columns+
  {:id {:value :id
        :header #(text :t.actions/application)}
   :resource {:value get-catalogue-items
              :header #(text :t.actions/resource)}
   :applicant {:value :applicantuserid
               :header #(text :t.actions/applicant)}
   :state {:value #(localize-state (:state %))
           :header #(text :t.actions/state)}
   :created {:value #(localize-time (:start %))
             :sort-value :start
             :header #(text :t.actions/created)}
   :handled {:value #(localize-time (:handled %))
             :sort-value :handled
             ;; NB!:
             :header #(text :t.actions/last-modified)}})

(defn column-header [column-definitions col]
  ((get-in column-definitions [col :header])))

(defn column-value [column-definitions col app]
  ((get-in column-definitions [col :value]) app))

(defn column-sort-value [column-definitions col app]
  ((or (get-in column-definitions [col :sort-value])
       (get-in column-definitions [col :value]))
   app))

(defn- row [column-definitions columns app]
  (into [:tr.action]
        (concat (for [c columns]
                  [:td {:data-th (column-header column-definitions c)}
                   (column-value column-definitions c app)])
                ;; buttons to show could be parameterized
                [[:td.commands (view-button app)]])))

(defn- flip [order]
  (case order
    :asc :desc
    :desc :asc))

(defn- change-sort [old-column old-order new-column]
  (if (= old-column new-column)
    [old-column (flip old-order)]
    [new-column :asc]))

(defn- apply-sorting [column-definitions [col order] apps]
  (let [sorted (sort-by #(column-sort-value column-definitions col %) apps)]
    (case order
      :asc sorted
      :desc (reverse sorted))))

(defn- table [column-definitions visible-columns [sort-column sort-order] set-sorting apps]
  [:table.rems-table.actions
   (into [:tbody
          (into [:tr]
                (for [c visible-columns]
                  [:th
                   {:on-click #(set-sorting (change-sort sort-column sort-order c))}
                   (column-header column-definitions c)
                   " "
                   (when (= c sort-column) (sort-symbol sort-order))]))]
         (map (fn [app] ^{:key (:id app)} [row column-definitions visible-columns app])
              (apply-sorting column-definitions [sort-column sort-order] apps)))])

(defn component
  "A table of applications.

   columns should be a subsequence of +all-columns+, for instance +default-columns+.

   sorting should be a pair [column order] where
     - order is :asc or :desc
     - column is one of :id :applicant :resource :created :state

   set-sorting is a callback that is called with a new sorting when it changes"
  [columns sorting set-sorting apps]
  (table +columns+ columns sorting set-sorting apps))

(def ^:private +example-applications+
  [{:id 1 :catalogue-items [{:title "Item 5"}] :state "draft" :applicantuserid "alice"
    :start "1980-01-02T13:45:00.000Z" :handled "2017-01-01T01:01:01:001Z"}
   {:id 2 :catalogue-items [{:title "Item 3"}] :state "applied" :applicantuserid "bob"
    :start "1971-02-03T23:59:00.000Z" :handled "2017-01-01T01:01:01:001Z"}
   {:id 3 :catalogue-items [{:title "Item 2"} {:title "Item 5"}] :state "approved" :applicantuserid "charlie"
    :start "1980-01-01T01:01:00.000Z" :handled "2017-01-01T01:01:01:001Z"}
   {:id 4 :catalogue-items [{:title "Item 2"}] :state "rejected" :applicantuserid "david"
    :start "1972-12-12T12:12:00.000Z" :handled "2017-01-01T01:01:01:001Z"}
   {:id 5 :catalogue-items [{:title "Item 2"}] :state "closed" :applicantuserid "ernie"
    :start "1972-12-12T12:12:00.000Z" :handled "2017-01-01T01:01:01:001Z"}])

(defn guide
  []
  [:div
   (component-info component)
   (example "empty list"
            [component +default-columns+ [:id :asc] prn []])
   (example "applications, default order"
            [component +default-columns+ [:id :asc] prn +example-applications+])
   (example "applications, descending date, all columns"
            [component +all-columns+ [:created :desc] prn +example-applications+])])
