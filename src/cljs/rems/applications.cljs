(ns rems.applications
  (:require [ajax.core :refer [GET]]
            [clojure.string :as string]
            [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.text :refer [localize-state localize-time text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- fetch-my-applications [user]
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

(rf/reg-sub
 ::sort
 (fn [db _]
   (or (::sort db) [:id :asc])))

(rf/reg-event-db
 ::sort
 (fn [db [_ order]]
   (assoc db ::sort order)))

(defn applications-page []
  (let [user @(rf/subscribe [:user])]
    (fetch-my-applications user))
  (let [apps @(rf/subscribe [::my-applications])
        sort @(rf/subscribe [::sort])
        set-sort #(rf/dispatch [::sort %])]
    (if (empty? apps)
      [:div.applications.alert.alert-success (text :t/applications.empty)]
      [application-list/component sort set-sort apps])))

(defn guide []
  [:div
   ;; TODO move guide stoff to application-list
   #_(example "applications empty"
            [applications []])
   #_(example "applications"
            [applications
             [{:id 1 :catalogue-items [{:title "Draft application"}] :state "draft" :applicantuserid "alice"}
              {:id 2 :catalogue-items [{:title "Applied application"}] :state "applied" :applicantuserid "bob"}
              {:id 3 :catalogue-items [{:title "Approved application"}] :state "approved" :applicantuserid "charlie"}
              {:id 4 :catalogue-items [{:title "Rejected application"}] :state "rejected" :applicantuserid "david"}
              {:id 5 :catalogue-items [{:title "Closed application"}] :state "closed" :applicantuserid "ernie"}]])])
