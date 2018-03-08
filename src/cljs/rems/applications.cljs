(ns rems.applications
  (:require [ajax.core :refer [GET]]
            [clojure.string :as string]
            [re-frame.core :as rf]
            [rems.text :refer [localize-state localize-time text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- fetch-my-applications []
  (GET "/api/applications/" {:handler #(rf/dispatch [::fetch-my-applications %])
                             :response-format :json
                             :keywords? true}))

(rf/reg-event-db
 ::fetch-my-applications
 (fn [db [_ applications]]
   (assoc db ::my-applications applications)))

(rf/reg-sub
 ::my-applications
 (fn [db _]
   (::my-applications db)))

(defn- applications-item [app]
  (into [:tbody
         [:tr.application
          [:td {:data-th (text :t.applications/application)} (:id app)]
          [:td {:data-th (text :t.applications/resource)} (string/join ", " (map :title (:catalogue-items app)))]
          [:td {:data-th (text :t.applications/state)} (localize-state (:state app))]
          [:td {:data-th (text :t.applications/created)} (localize-time (:start app))]
          [:td [:a.btn.btn-primary
                {:href (str "#/application/" (:id app))}
                (text :t/applications.view)]]]]))

(defn- applications
  [apps]
  (if (empty? apps)
    [:div.applications.alert.alert-success (text :t/applications.empty)]
    [:table.rems-table.applications
     (into [:tbody
            [:tr
             [:th (text :t.applications/application)]
             [:th (text :t.applications/resource)]
             [:th (text :t.applications/state)]
             [:th (text :t.applications/created)]]])
     (for [app apps]
       (applications-item app))]))

(defn applications-page []
  (fetch-my-applications)
  (let [apps @(rf/subscribe [::my-applications])]
    (applications apps)))

(defn guide []
  [:div
   (example "applications empty"
            (applications []))
   (example "applications"
            (applications
             [{:id 1 :catalogue-items [{:title "Draft application"}] :state "draft" :applicantuserid "alice"}
              {:id 2 :catalogue-items [{:title "Applied application"}] :state "applied" :applicantuserid "bob"}
              {:id 3 :catalogue-items [{:title "Approved application"}] :state "approved" :applicantuserid "charlie"}
              {:id 4 :catalogue-items [{:title "Rejected application"}] :state "rejected" :applicantuserid "david"}
              {:id 5 :catalogue-items [{:title "Closed application"}] :state "closed" :applicantuserid "ernie"}]))])