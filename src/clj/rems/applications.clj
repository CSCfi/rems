(ns rems.applications
  (:require [rems.context :as context]
            [rems.text :refer [text]]
            [rems.db.core :as db]))

(defn applications-item [app]
  [:tr
   [:td (:id app)]
   [:td (:catid app)]
   [:td (:applicantuserid app)]])

(defn applications
  ([]
   (applications (db/get-applications)))
  ([apps]
   [:table.rems-table
    [:tr
     [:th (text :t.applications/application)]
     [:th (text :t.applications/resource)]
     [:th (text :t.applications/user)]]
    (for [app apps]
      (applications-item app))]))

