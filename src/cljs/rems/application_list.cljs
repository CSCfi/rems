(ns rems.application-list
  (:require [clojure.string :as str]
            [rems.text :refer [localize-state localize-time text]]))

(defn- view-button [app]
  [:a.btn.btn-primary
   {:href (str "#/application/" (:id app))}
   (text :t.applications/view)])

(defn- get-catalogue-items [app]
  (str/join ", " (map :title (:catalogue-items app))))

(defn- row [app]
  [:tr.action
   [:td {:data-th (text :t.actions/application)} (:id app)]
   [:td {:data-th (text :t.actions/resource)} (get-catalogue-items app)]
   [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
   ;; make state column hideable?
   [:td {:data-th (text :t.actions/state)} (localize-state (:state app))]
   [:td {:data-th (text :t.actions/created)} (localize-time (:start app))]
   ;; buttons to show could be parameterized
   [:td.commands (view-button app)]])

(defn- table [apps]
  [:table.rems-table.actions
   (into [:tbody
          [:tr
           [:th (text :t.actions/application)]
           [:th (text :t.actions/resource)]
           [:th (text :t.actions/applicant)]
           [:th (text :t.actions/state)]
           [:th (text :t.actions/created)]
           [:th]]]
         (map row apps))])

(def ^:private +sort-functions+
  {:id :id
   :applicant :applicantuserid
   :resource get-catalogue-items
   :created :start
   :state (comp localize-state :state)})

(defn component
  "A table of applications.

   sort-order can be:
     :id
     :applicant
     :resource
     :created
     :state"
  [sort-order apps]
  (table (sort-by (get +sort-functions+ sort-order) apps)))
