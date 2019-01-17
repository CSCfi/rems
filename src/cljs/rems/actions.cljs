(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [clojure.string :as str]
            [medley.core :refer [distinct-by]]
            [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-state localize-time text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc ::loading-actions? true)
            (dissoc ::actions ::handled-actions)) ; zero state that should be reloaded, good for performance
    ::fetch-actions nil}))

;;;; actions

(defn- fetch-actions []
  (fetch "/api/actions/" {:handler #(rf/dispatch [::fetch-actions-result %])}))

(rf/reg-fx
 ::fetch-actions
 (fn [_]
   (fetch-actions)))

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
 ::start-fetch-handled-actions
 (fn [{:keys [db]} _]
   {:db (assoc db ::loading-handled-actions? true)
    ::fetch-handled-actions []}))

(defn- fetch-handled-actions []
  (fetch "/api/actions/handled" {:handler #(rf/dispatch [::fetch-handled-actions-result %])}))

(rf/reg-fx
 ::fetch-handled-actions
 (fn [_]
   (fetch-handled-actions)))

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

;;;; table sorting

;; Because we want to display multiple independently sortable
;; application tables, we store a map of sort types in the db.

(rf/reg-sub
 ::sorting
 (fn [db [_ key]]
   (get-in db [::sorting key] {:sort-column :last-modified
                               :sort-order :desc})))

(rf/reg-event-db
 ::set-sorting
 (fn [db [_ key sorting]]
   (assoc-in db [::sorting key] sorting)))

;;;; UI

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
  [apps]
  (if (empty? apps)
    [:div.actions.alert.alert-success (text :t.actions/empty)]
    [application-list/component
     application-list/+all-columns+
     @(rf/subscribe [::sorting ::open-applications])
     #(rf/dispatch [::set-sorting ::open-applications %])
     apps]))

(defn- handled-applications
  "Creates a table containing a list of handled applications.

  The function takes the following parameters as arguments:
  key:         key to use for table ordering in re-frame
  apps:        collection of apps to be shown
  top-buttons: a set of extra buttons that will be shown on top of the table. This could include f.ex 'export as pdf' button."
  [apps top-buttons loading?]
  (if loading?
    [spinner/big]
    (if (empty? apps)
      [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]
      [:div
       top-buttons
       [application-list/component
        [:id :description :resource :applicant :state :last-modified :view]
        @(rf/subscribe [::sorting ::handled-applications])
        #(rf/dispatch [::set-sorting ::handled-applications %])
        apps]])))

(defn actions-page [reviews]
  (let [actions (rf/subscribe [::actions])
        loading-actions? (rf/subscribe [::loading-actions?])
        handled-actions (rf/subscribe [::handled-actions])
        loading-handled-actions? (rf/subscribe [::loading-handled-actions?])]
    (fn [reviews]
      (if @loading-actions?
        [spinner/big]
        [:div.spaced-sections
         (when (or (:reviewer? @actions) (:approver? @actions))
           [collapsible/component
            {:id "open-approvals"
             :open? true
             :title (text :t.actions/open-approvals)
             :collapse [open-applications
                        (distinct-by :id (concat (:reviews @actions)
                                                 (:approvals @actions)))]}])
         (when (or (:reviewer? @actions) (:approver? @actions))
           [collapsible/component
            {:id "handled-approvals"
             :on-open #(rf/dispatch [:rems.actions/start-fetch-handled-actions])
             :title (text :t.actions/handled-approvals)
             :collapse [handled-applications
                        (distinct-by :id (concat (:handled-reviews @handled-actions)
                                                 (:handled-approvals @handled-actions)))
                        @loading-handled-actions?]}])]))))
