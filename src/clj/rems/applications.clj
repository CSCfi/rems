(ns rems.applications
  (:require [rems.context :as context]
            [rems.text :refer [text]]
            [rems.db.core :as db]))

(defn get-applications []
  (doall
   (for [a (db/get-applications)]
     (assoc a :catalogue-item
            (get-in (db/get-localized-catalogue-item {:id (:catid a)})
                    [:localizations context/*lang*])))))

(defn applications-item [app]
  [:tr
   [:td (:id app)]
   [:td (get-in app [:catalogue-item :title])]
   [:td (:applicantuserid app)]])

(defn applications
  ([]
   (applications (get-applications)))
  ([apps]
   [:table.rems-table
    [:tr
     [:th (text :t.applications/application)]
     [:th (text :t.applications/resource)]
     [:th (text :t.applications/user)]]
    (for [app apps]
      (applications-item app))]))

