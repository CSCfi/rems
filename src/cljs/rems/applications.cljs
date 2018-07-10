(ns rems.applications
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.text :refer [localize-state localize-time text]]
            [rems.util :refer [fetch]]))

(defn- fetch-my-applications []
  (fetch "/api/applications/" {:handler #(rf/dispatch [::fetch-my-applications %])}))

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
   (or (::sort db) [:created :desc])))

(rf/reg-event-db
 ::sort
 (fn [db [_ order]]
   (assoc db ::sort order)))

(defn applications-page []
  (fetch-my-applications)
  (let [apps @(rf/subscribe [::my-applications])
        sort @(rf/subscribe [::sort])
        set-sort #(rf/dispatch [::sort %])]
    (if (empty? apps)
      [:div.applications.alert.alert-success (text :t/applications.empty)]
      [application-list/component application-list/+all-columns+ sort set-sort apps])))
