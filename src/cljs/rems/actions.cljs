(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [ajax.core :refer [GET]]
            [clojure.string :as str]
            [re-frame.core :as re-frame]
            [rems.application-list :as application-list]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.text :refer [localize-state localize-time text]]))

(defn- fetch-actions []
  (GET "/api/actions/" {:handler #(re-frame/dispatch [::fetch-actions-result %])
                        :response-format :json
                        :keywords? true}))

(re-frame/reg-fx
 ::fetch-actions
 (fn [_]
   (fetch-actions)))

(re-frame/reg-event-fx
 ::start-fetch-actions
 (fn [{:keys [db]} _]
   {::fetch-actions []}))

(re-frame/reg-event-db
 ::fetch-actions-result
 (fn [db [_ result]]
   (assoc db ::actions result) ))

(re-frame/reg-sub
 ::actions
 (fn [db _]
   (::actions db)))

;; Because we want to display multiple independently sortable
;; application tables, we store a map of sort types in the db.
;;
;; Use (re-frame/dispatch [::sort :my-key [:field :asc]]) to set a
;; sort type, and (re-frame/subscribe [::sort :my-key]) to get it
;; back.
;;
;; See rems.application-list for more info about sort types.

(re-frame/reg-sub
 ::sort
 (fn [db [_ key]]
   (get-in db [::sort key] [:id :asc])))

(re-frame/reg-event-db
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
     application-list/+default-columns+
     @(re-frame/subscribe [::sort key])
     #(re-frame/dispatch [::sort key %])
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
  [key apps top-buttons]
  (if (empty? apps)
    [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]
    [:div
     top-buttons
     [application-list/component
      [:id :resource :applicant :state :handled]
      @(re-frame/subscribe [::sort key])
      #(re-frame/dispatch [::sort key %])
      apps]]))

(defn- handled-approvals [apps]
  [handled-applications ::handled-approvals apps [report-buttons]])

(defn- handled-reviews
  [apps]
  [handled-applications ::handled-reviews apps nil])

;; TODO ensure ::actions is loaded when navigating to page
(defn actions-page [reviews]
  (re-frame/dispatch [::start-fetch-actions])
  (let [actions @(re-frame/subscribe [::actions])]
    [:div
     (when (:reviewer? actions)
       [:div
        [:div
         [collapsible/component
          {:id "open-reviews"
           :open? true
           :title (text :t.actions/open-reviews)
           :collapse [open-reviews (:reviews actions)]}]
         [:div.my-3
          [collapsible/component
           {:id "handled-reviews"
            :title (text :t.actions/handled-reviews)
            :collapse [handled-reviews (:handled-reviews actions)]}]]]])
     (when (:approver? actions)
       [:div
        [:div
         [collapsible/component
          {:id "open-approvals"
           :open? true
           :title (text :t.actions/open-approvals)
           :collapse [open-approvals (:approvals actions)]}]
         [:div.mt-3
          [collapsible/component
           {:id "handled-approvals"
            :title (text :t.actions/handled-approvals)
            :collapse [handled-approvals (:handled-approvals actions)]}]]]])]))
