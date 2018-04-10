(ns rems.application-list
  (:require [clojure.string :as str]
            [rems.text :refer [localize-state localize-time text]]))

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

(defn- table [apps]
  [:table.rems-table.actions
   (into [:tbody
          (into [:tr]
                (for [{:keys [header]} +columns+]
                  [:th (header)]))]
         (map row apps))])

(defn- sort-by-column [col apps]
  (let [fun (get
             (zipmap (map :name +columns+) (map :getter +columns+))
             col)]
    (sort-by fun apps)))

(defn component
  "A table of applications.

   sort-order can be:
     :id
     :applicant
     :resource
     :created
     :state"
  [sort-order apps]
  (table (sort-by-column sort-order apps)))
