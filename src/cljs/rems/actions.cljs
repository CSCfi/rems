(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [ajax.core :refer [GET]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-state localize-time text]]
            [rems.util :refer [redirect-when-unauthorized]]))

(defn- fetch-actions []
  (GET "/api/actions/" {:handler #(rf/dispatch [::fetch-actions-result %])
                        :error-handler redirect-when-unauthorized
                        :response-format :json
                        :keywords? true}))

(defn- fetch-handled-actions []
  (GET "/api/actions/handled" {:handler #(rf/dispatch [::fetch-handled-actions-result %])
                               :error-handler redirect-when-unauthorized
                               :response-format :json
                               :keywords? true}))

(rf/reg-fx
 ::fetch-actions
 (fn [_]
   (fetch-actions)))

(rf/reg-fx
 ::fetch-handled-actions
 (fn [_]
   (fetch-handled-actions)))

(rf/reg-event-fx
 ::start-fetch-actions
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc ::loading-actions? true)
            (dissoc ::actions ::handled-actions)) ; zero state that should be reloaded, good for performance
    ::fetch-actions []}))

(rf/reg-event-fx
 ::start-fetch-handled-actions
 (fn [{:keys [db]} _]
   {:db (assoc db ::loading-handled-actions? true)
    ::fetch-handled-actions []}))

(rf/reg-event-db
 ::fetch-actions-result
 (fn [db [_ result]]
   (-> db
       (assoc ::actions result)
       (dissoc ::loading-actions?))))

(rf/reg-event-db
 ::fetch-handled-actions-result
 (fn [db [_ result]]
   (-> db
       (assoc ::handled-actions result)
       (dissoc ::loading-handled-actions?))))

(rf/reg-sub
 ::actions
 (fn [db _]
   (::actions db)))

(rf/reg-sub
 ::loading-actions?
 (fn [db _]
   (::loading-actions? db)))

(rf/reg-sub
 ::handled-actions
 (fn [db _]
   (::handled-actions db)))

(rf/reg-sub
 ::loading-handled-actions?
 (fn [db _]
   (::loading-handled-actions? db)))

;; Because we want to display multiple independently sortable
;; application tables, we store a map of sort types in the db.
;;
;; Use (rf/dispatch [::sort :my-key [:field :asc]]) to set a
;; sort type, and (rf/subscribe [::sort :my-key]) to get it
;; back.
;;
;; See rems.application-list for more info about sort types.

(rf/reg-sub
 ::sort
 (fn [db [_ key]]
   (get-in db [::sort key] [:last-modified :desc])))

(rf/reg-event-db
 ::sort
 (fn [db [_ key sort]]
   (assoc-in db [::sort key] sort)))

;; TODO not implemented
(defn- load-application-states-button []
  [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#load-application-states-modal" :disabled true}
   (text :t.actions/load-application-states)])

(defn- export-entitlements-button []
  [:a.btn.btn-secondary
   {:href "/entitlements.csv"}
   (text :t.actions/export-entitlements)])

;; TODO not implemented
(defn- show-publications-button []
  [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#show-publications-modal" :disabled true}
   (text :t.actions/show-publications)])

;; TODO not implemented
(defn- show-throughput-times-button []
  [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#show-throughput-times-modal" :disabled true}
   (text :t.actions/show-throughput-times)])

(defn- report-buttons []
  [:div.form-actions.inline
   [load-application-states-button]
   [export-entitlements-button]
   [show-publications-button]
   [show-throughput-times-button]])

(defn- open-applications
  [key apps]
  (if (empty? apps)
    [:div.actions.alert.alert-success (text :t.actions/empty)]
    [application-list/component
     application-list/+all-columns+
     @(rf/subscribe [::sort key])
     #(rf/dispatch [::sort key %])
     apps]))

(defn- open-reviews [apps]
  [open-applications ::open-applications apps])

(defn- open-approvals [apps]
  [open-applications ::open-approvals apps])

(defn- handled-applications
  "Creates a table containing a list of handled applications.

  The function takes the following parameters as arguments:
  key:         key to use for table ordering in re-frame
  apps:        collection of apps to be shown
  top-buttons: a set of extra buttons that will be shown on top of the table. This could include f.ex 'export as pdf' button."
  [key apps top-buttons loading?]
  (if loading?
    [spinner/big]
    (if (empty? apps)
      [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]
      [:div
       top-buttons
       [application-list/component
        [:id :resource :applicant :state :last-modified :view]
        @(rf/subscribe [::sort key])
        #(rf/dispatch [::sort key %])
        apps]])))

(defn- handled-approvals [apps loading?]
  [handled-applications ::handled-approvals apps [report-buttons] loading?])

(defn- handled-reviews
  [apps loading?]
  [handled-applications ::handled-reviews apps nil loading?])

(defn actions-page [reviews]
  (let [actions (rf/subscribe [::actions])
        loading-actions? (rf/subscribe [::loading-actions?])
        handled-actions (rf/subscribe [::handled-actions])
        loading-handled-actions? (rf/subscribe [::loading-handled-actions?])]
    (fn [reviews]
      (if @loading-actions?
        [spinner/big]
        [:div
         (when (:reviewer? @actions)
           [:div
            [:div
             [collapsible/component
              {:id "open-reviews"
               :open? true
               :title (text :t.actions/open-reviews)
               :collapse [open-reviews (:reviews @actions)]}]
             [:div.my-3
              [collapsible/component
               {:id "handled-reviews"
                :on-open #(rf/dispatch [:rems.actions/start-fetch-handled-actions])
                :title (text :t.actions/handled-reviews)
                :collapse [handled-reviews (:handled-reviews @handled-actions) @loading-handled-actions?]}]]]])
         (when (:approver? @actions)
           [:div
            [:div
             [collapsible/component
              {:id "open-approvals"
               :open? true
               :title (text :t.actions/open-approvals)
               :collapse [open-approvals (:approvals @actions)]}]
             [:div.mt-3
              [collapsible/component
               {:id "handled-approvals"
                :on-open #(rf/dispatch [:rems.actions/start-fetch-handled-actions])
                :title (text :t.actions/handled-approvals)
                :collapse [handled-approvals (:handled-approvals @handled-actions) @loading-handled-actions?]}]]]])]))))
