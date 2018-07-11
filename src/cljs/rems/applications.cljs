(ns rems.applications
  (:require [ajax.core :refer [GET]]
            [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-state localize-time text]]
            [rems.util :refer [redirect-when-unauthorized]]))

(defn- fetch-my-applications []
  (GET "/api/applications/" {:handler #(rf/dispatch [::fetch-my-applications-result %])
                             :error-handler redirect-when-unauthorized
                             :response-format :json
                             :keywords? true}))

(rf/reg-fx
 ::fetch-my-applications
 (fn [_]
   (fetch-my-applications)))

(rf/reg-event-db
 ::fetch-my-applications-result
 (fn [db [_ applications]]
   (-> db
       (assoc ::my-applications applications)
       (dissoc ::loading?))))

(rf/reg-event-fx
 ::start-fetch-my-applications
 (fn [{:keys [db]} [_ applications]]
   {:db (-> db
            (assoc ::loading? true)
            (dissoc ::my-applications))
    ::fetch-my-applications []}))

(rf/reg-sub
 ::my-applications
 (fn [db _]
   (::my-applications db)))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

(rf/reg-sub
 ::sort
 (fn [db _]
   (or (::sort db) [:created :desc])))

(rf/reg-event-db
 ::sort
 (fn [db [_ order]]
   (assoc db ::sort order)))

(defn applications-page []
  (let [apps (rf/subscribe [::my-applications])
        loading? (rf/subscribe [::loading?])
        sort (rf/subscribe [::sort])
        set-sort #(rf/dispatch [::sort %])]
    (fn []
      [:div
       [:h2 (text :t.applications/applications)]
       (cond @loading?
             [spinner/big]

             (empty? @apps)
             [:div.applications.alert.alert-success (text :t/applications.empty)]

             :else
             [application-list/component application-list/+all-columns+ @sort set-sort @apps])])))
