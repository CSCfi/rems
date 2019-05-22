(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::actions ::handled-actions) ; zero state that should be reloaded, good for performance
    :dispatch [::fetch-actions]}))

;;;; actions

(rf/reg-event-fx
 ::fetch-actions
 (fn [{:keys [db]} _]
   (fetch "/api/applications/todo"
          {:handler #(rf/dispatch [::fetch-actions-result %])})
   {:db (assoc db ::loading-actions? true)}))

(rf/reg-event-db
 ::fetch-actions-result
 (fn [db [_ result]]
   (-> db
       (assoc ::actions result)
       (dissoc ::loading-actions?))))

(rf/reg-sub
 ::actions
 (fn [db _]
   (::actions db)))

(rf/reg-sub
 ::loading-actions?
 (fn [db _]
   (::loading-actions? db)))

;;;; handled actions

(rf/reg-event-fx
 ::fetch-handled-actions
 (fn [{:keys [db]} _]
   (fetch "/api/applications/handled"
          {:handler #(rf/dispatch [::fetch-handled-actions-result %])})
   {:db (assoc db ::loading-handled-actions? true)}))

(rf/reg-event-db
 ::fetch-handled-actions-result
 (fn [db [_ result]]
   (-> db
       (assoc ::handled-actions result)
       (dissoc ::loading-handled-actions?))))

(rf/reg-sub
 ::handled-actions
 (fn [db _]
   (::handled-actions db)))

(rf/reg-sub
 ::loading-handled-actions?
 (fn [db _]
   (::loading-handled-actions? db)))

;;;; UI

;; TODO not implemented
(defn- load-application-states-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#load-application-states-modal" :disabled true}
   (text :t.actions/load-application-states)])

(defn- export-entitlements-button []
  [:a.btn.btn-secondary
   {:href "/entitlements.csv"}
   (text :t.actions/export-entitlements)])

;; TODO not implemented
(defn- show-publications-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#show-publications-modal" :disabled true}
   (text :t.actions/show-publications)])

;; TODO not implemented
(defn- show-throughput-times-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#show-throughput-times-modal" :disabled true}
   (text :t.actions/show-throughput-times)])

(defn- report-buttons []
  [:div.form-actions.inline
   [load-application-states-button]
   [export-entitlements-button]
   [show-publications-button]
   [show-throughput-times-button]])

(defn application-list-defaults []
  (let [config @(rf/subscribe [:rems.config/config])
        id-column (get config :application-id-column :id)]
    {:visible-columns #{id-column :description :resource :applicant :state :submitted :last-activity :view}
     :default-sort-column :last-activity
     :default-sort-order :desc}))

(defn- open-applications []
  (let [applications ::actions]
    (if (empty? @(rf/subscribe [applications]))
      [:div.actions.alert.alert-success (text :t.actions/empty)]
      [application-list/component2
       (-> (application-list-defaults)
           (assoc :id ::open-applications
                  :applications applications))])))

(defn- handled-applications []
  (let [applications ::handled-actions]
    (cond
      @(rf/subscribe [::loading-handled-actions?])
      [spinner/big]

      (empty? @(rf/subscribe [applications]))
      [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]

      :else
      [application-list/component2
       (-> (application-list-defaults)
           (update :visible-columns disj :submitted)
           (assoc :id ::handled-applications
                  :applications applications))])))

(defn actions-page []
  [:div
   [document-title (text :t.navigation/actions)]
   (if @(rf/subscribe [::loading-actions?])
     [spinner/big]
     [:div.spaced-sections
      [collapsible/component
       {:id "open-approvals"
        :open? true
        :title (text :t.actions/open-approvals)
        :collapse [open-applications]}]
      [collapsible/component
       {:id "handled-approvals"
        :on-open #(rf/dispatch [::fetch-handled-actions])
        :title (text :t.actions/handled-approvals)
        :collapse [handled-applications]}]])])
