(ns rems.applications
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
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
                   [::fetch-all-applications])
                 [:rems.table2/reset]]}))

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

;;;; UI

(defn- application-list [opts]
  (let [apps @(rf/subscribe [(:applications opts)])]
    (cond (::loading? opts)
          [spinner/big]

          (empty? apps)
          [:div.applications.alert.alert-success (text :t.applications/empty)]

          :else
          (let [config @(rf/subscribe [:rems.config/config])
                id-column (get config :application-id-column :id)]
            [application-list/component
             (merge {:visible-columns #{id-column :description :resource :state :created :submitted :last-activity :view}
                     :default-sort-column :created
                     :default-sort-order :desc}
                    opts)]))))

(defn applications-page []
  (let [identity @(rf/subscribe [:identity])]
    [:div
     [document-title (text :t.applications/applications)]
     (when (roles/show-all-applications? (:roles identity))
       [:h2 (text :t.applications/my-applications)])
     [application-list {:id ::my-applications
                        :applications ::my-applications
                        ::loading? @(rf/subscribe [::loading-my-applications?])}]
     (when (roles/show-all-applications? (:roles identity))
       [:div
        [:h2 (text :t.applications/all-applications)]
        [application-list {:id ::all-applications
                           :applications ::all-applications
                           ::loading? @(rf/subscribe [::loading-all-applications?])}]])]))
