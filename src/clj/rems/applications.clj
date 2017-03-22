(ns rems.applications
  (:require [rems.context :as context]
            [rems.guide :refer :all]
            [rems.text :refer [text]]
            [rems.db.core :as db]
            [rems.db.applications :refer [get-applications]]))

(defn- localize-state [state]
  (case state
    "draft" :t.applications.states/draft
    "applied" :t.applications.states/applied
    :t.applications.states/unknown))

(defn- applications-item [app]
  [:tr
   [:td (:id app)]
   [:td (get-in app [:catalogue-item :title])]
   [:td (text (localize-state (:state app)))]
   [:td (:start app)]
   [:td [:a.btn.btn-primary
         {:href (str "/form/" (:catid app) "/" (:id app))}
         (text :t/applications.view)]]])

(defn applications
  ([]
   (applications (get-applications)))
  ([apps]
   [:table.rems-table
    [:tr
     [:th (text :t.applications/application)]
     [:th (text :t.applications/resource)]
     [:th (text :t.applications/state)]
     [:th (text :t.applications/created)]]
    (for [app apps]
      (applications-item app))]))

(defn guide
  []
  (example "applications"
           (applications
            [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid 2}
             {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid 4}])))
