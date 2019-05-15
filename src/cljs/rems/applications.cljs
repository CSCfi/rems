(ns rems.applications
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-state localize-time text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::my-applications ::all-applications)
    :dispatch-n [[::fetch-my-applications]
                 (when (roles/show-all-applications? (:roles (:identity db)))
                   [::fetch-all-applications])]}))

;;;; my applications

(rf/reg-event-fx
 ::fetch-my-applications
 (fn [{:keys [db]} _]
   (fetch "/api/my-applications"
          {:handler #(rf/dispatch [::fetch-my-applications-result %])})
   {:db (assoc db ::loading-my-applications? true)}))

(rf/reg-event-db
 ::fetch-my-applications-result
 (fn [db [_ applications]]
   (-> db
       (assoc ::my-applications applications)
       (dissoc ::loading-my-applications?))))

(rf/reg-sub
 ::my-applications
 (fn [db _]
   (::my-applications db)))

(rf/reg-sub
 ::loading-my-applications?
 (fn [db _]
   (::loading-my-applications? db)))

;;;; all applications

(rf/reg-event-fx
 ::fetch-all-applications
 (fn [{:keys [db]} _]
   (fetch "/api/applications"
          {:handler #(rf/dispatch [::fetch-all-applications-result %])})
   {:db (assoc db ::loading-all-applications? true)}))

(rf/reg-event-db
 ::fetch-all-applications-result
 (fn [db [_ applications]]
   (-> db
       (assoc ::all-applications applications)
       (dissoc ::loading-all-applications?))))

(rf/reg-sub
 ::all-applications
 (fn [db _]
   (::all-applications db)))

(rf/reg-sub
 ::loading-all-applications?
 (fn [db _]
   (::loading-all-applications? db)))

;;;; table sorting

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :created
        :sort-order :desc})))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))

(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))

;;;; UI

;; XXX: the application lists share sorting and filtering state
(defn- application-list [apps loading?]
  (cond loading?
        [spinner/big]

        (empty? apps)
        [:div.applications.alert.alert-success (text :t.applications/empty)]

        :else
        [application-list/component
         {:visible-columns (into [(get @(rf/subscribe [:rems.config/config]) :application-id-column :id)]
                                 [:description :resource :state :created :submitted :last-activity :view])
          :sorting (assoc @(rf/subscribe [::sorting])
                          :set-sorting #(rf/dispatch [::set-sorting %]))
          :filtering (assoc @(rf/subscribe [::filtering])
                            :set-filtering #(rf/dispatch [::set-filtering %]))
          :items apps}]))

(defn applications-page []
  (let [apps @(rf/subscribe [::my-applications])
        identity @(rf/subscribe [:identity])
        loading? @(rf/subscribe [::loading-my-applications?])]
    [:div
     [:h1 (text :t.applications/applications)]
     (when (roles/show-all-applications? (:roles identity))
       [:h2 (text :t.applications/my-applications)])
     [application-list apps loading?]
     (let [apps @(rf/subscribe [::all-applications])
           loading? @(rf/subscribe [::loading-all-applications?])]
       (when (roles/show-all-applications? (:roles identity))
         [:div
          [:h2 (text :t.applications/all-applications)]
          [application-list apps loading?]]))]))
