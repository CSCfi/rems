(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.search :as search]
            [rems.text :refer [text]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db
                ::todo-applications
                ::handled-applications)
    :dispatch-n [[::todo-applications]
                 [:rems.table/reset]]}))

(search/reg-fetcher ::todo-applications "/api/applications/todo")
(search/reg-fetcher ::handled-applications "/api/applications/handled")

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

(defn actions-page []
  [:div
   [document-title (text :t.navigation/actions)]
   [:div.spaced-sections
    [collapsible/component
     {:id "todo-applications"
      :open? true
      :title (text :t.actions/todo-applications)
      :collapse [:<>
                 [search/search-field {:id "todo-search"
                                       :on-search #(rf/dispatch [::todo-applications %])
                                       :searching? @(rf/subscribe [::todo-applications :searching?])}]
                 [search/application-search-tips]
                 [application-list/component {:applications ::todo-applications
                                              :empty-message :t.actions/empty
                                              :hidden-columns #{:created}
                                              :default-sort-column :last-activity
                                              :default-sort-order :desc}]]}]
    [collapsible/component
     {:id "handled-applications"
      :on-open #(rf/dispatch [::handled-applications])
      :title (text :t.actions/handled-applications)
      :collapse [:<>
                 [search/search-field {:id "handled-search"
                                       :on-search #(rf/dispatch [::handled-applications %])
                                       :searching? @(rf/subscribe [::handled-applications :searching?])}]
                 [search/application-search-tips]
                 [application-list/component {:applications ::handled-applications
                                              :empty-message :t.actions/no-handled-yet
                                              :hidden-columns #{:created :submitted}
                                              :default-sort-column :last-activity
                                              :default-sort-order :desc}]]}]]])
