(ns rems.applications
  (:require [rems.context :as context]
            [rems.text :refer [text]]
            [rems.db.core :as db]
            [rems.db.applications :refer [get-applications]]))

(defn localize-state [state]
  (case state
    "draft" :t.applications.states/draft
    "applied" :t.applications.states/applied
    :t.applications.states/unknown))

(defn applications-item [app]
  [:tr
   [:td [:a.catalogue-item-link {:href (str "/form/" (:catid app) "/" (:id app))} (:id app)]]
   [:td (get-in app [:catalogue-item :title])]
   [:td (text (localize-state (:state app)))]
   [:td (:applicantuserid app)]])

(defn applications
  ([]
   (applications (get-applications)))
  ([apps]
   [:table.rems-table
    [:tr
     [:th (text :t.applications/application)]
     [:th (text :t.applications/resource)]
     [:th (text :t.applications/state)]
     [:th (text :t.applications/user)]]
    (for [app apps]
      (applications-item app))]))
