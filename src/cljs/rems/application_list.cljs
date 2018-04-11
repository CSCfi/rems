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

(def ^:private +columns+
  [{:name :id
    :getter :id
    :header #(text :t.actions/application)}
   {:name :resource
    :getter get-catalogue-items
    :header #(text :t.actions/resource)}
   {:name :applicant
    :getter :applicantuserid
    :header #(text :t.actions/applicant)}
   {:name :state
    :getter #(localize-state (:state %))
    :header #(text :t.actions/state)}
   {:name :created
    :getter #(localize-time (:start %))
    :header #(text :t.actions/created)}])

(defn- row [app]
  (into [:tr.action]
        (concat (for [{:keys [header getter]} +columns+]
                  [:td {:data-th (header)} (getter app)])
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

(defn- table [[sort-column sort-order] set-sorting apps]
  [:table.rems-table.actions
   (into [:tbody
          (into [:tr]
                (for [{:keys [name header]} +columns+]
                  [:th
                   {:on-click #(set-sorting (change-sort sort-column sort-order name))}
                   (header)
                   (when (= name sort-column) (sort-symbol sort-order))]))]
         (map row apps))])

(defn- apply-sorting [[col order] apps]
  (let [fun (get
             (zipmap (map :name +columns+) (map :getter +columns+))
             col)
        sorted (sort-by fun apps)]
    (case order
      :asc sorted
      :desc (reverse sorted))))

(defn component
  "A table of applications.

   sorting should be a pair [column order] where
     - order is :asc or :desc
     - column is one of :id :applicant :resource :created :state

   set-sorting is a callback that is called with a new sorting when it changes"
  [sorting set-sorting apps]
  (table sorting set-sorting (apply-sorting sorting apps)))

(def ^:private +example-applications+
  [{:id 1 :catalogue-items [{:title "Item 5"}] :state "draft" :applicantuserid "alice"
    :start "1980-01-02T13:45:00.000Z"}
   {:id 2 :catalogue-items [{:title "Item 3"}] :state "applied" :applicantuserid "bob"
    :start "1971-02-03T23:59:00.000Z"}
   {:id 3 :catalogue-items [{:title "Item 2"} {:title "Item 5"}] :state "approved" :applicantuserid "charlie"
    :start "1980-01-01T01:01:00.000Z"}
   {:id 4 :catalogue-items [{:title "Item 2"}] :state "rejected" :applicantuserid "david"
    :start "1972-12-12T12:12:00.000Z"}
   {:id 5 :catalogue-items [{:title "Item 2"}] :state "closed" :applicantuserid "ernie"
    :start "1972-12-12T12:12:00.000Z"}])

(defn guide
  []
  [:div
   (component-info component)
   (example "empty list"
            [component [:id :asc] prn []])
   (example "applications, default order"
            [component [:id :asc] prn +example-applications+])
   (example "applications, descending date"
            [component [:created :desc] prn +example-applications+])])
