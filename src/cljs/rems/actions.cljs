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
   {:db (dissoc db ::todo-applications ::handled-applications)
    :dispatch-n [[::fetch-todo-applications]
                 [:rems.table/reset]]}))

;;;; applications to do

(rf/reg-event-fx
 ::fetch-todo-applications
 (fn [{:keys [db]} [_ query]]
   ;; do only one fetch at a time - will retry after the pending fetch is finished
   (when-not (::loading-todo-applications? db)
     (fetch "/api/applications/todo"
            {:url-params (when query
                           {:query query})
             :handler #(rf/dispatch [::fetch-todo-applications-result % query])}))
   {:db (assoc db
               ::todo-applications-query query
               ::loading-todo-applications? true)}))

(rf/reg-event-db
 ::fetch-todo-applications-result
 (fn [db [_ result query]]
   ;; fetch again if the query that just finished was not the latest
   (when-not (= query (::todo-applications-query db))
     (rf/dispatch [::fetch-todo-applications (::todo-applications-query db)]))
   (-> db
       (assoc ::todo-applications result)
       (dissoc ::loading-todo-applications?))))

(rf/reg-sub
 ::todo-applications
 (fn [db _]
   (::todo-applications db)))

(rf/reg-sub
 ::loading-todo-applications?
 (fn [db _]
   (::loading-todo-applications? db)))

;;;; handled applications

(rf/reg-event-fx
 ::fetch-handled-applications
 (fn [{:keys [db]} [_ query]]
   ;; do only one fetch at a time - will retry after the pending fetch is finished
   (when-not (::loading-handled-applications? db)
     (fetch "/api/applications/handled"
            {:url-params (when query
                           {:query query})
             :handler #(rf/dispatch [::fetch-handled-applications-result % query])}))
   {:db (assoc db
               ::handled-applications-query query
               ::loading-handled-applications? true)}))

(rf/reg-event-db
 ::fetch-handled-applications-result
 (fn [db [_ result query]]
   ;; fetch again if the query that just finished was not the latest
   (when-not (= query (::handled-applications-query db))
     (rf/dispatch [::fetch-handled-applications (::handled-applications-query db)]))
   (-> db
       (assoc ::handled-applications result)
       (dissoc ::loading-handled-applications?))))

(rf/reg-sub
 ::handled-applications
 (fn [db _]
   (::handled-applications db)))

(rf/reg-sub
 ::loading-handled-applications?
 (fn [db _]
   (::loading-handled-applications? db)))

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

(defn- todo-applications []
  (let [applications ::todo-applications]
    (cond
      @(rf/subscribe [::loading-todo-applications?])
      [spinner/big]

      (empty? @(rf/subscribe [applications]))
      [:div.actions.alert.alert-success (text :t.actions/empty)]

      :else
      [application-list/component
       (-> (application-list-defaults)
           (assoc :id applications
                  :applications applications))])))

(defn- handled-applications []
  (let [applications ::handled-applications]
    (cond
      @(rf/subscribe [::loading-handled-applications?])
      [spinner/big]

      (empty? @(rf/subscribe [applications]))
      [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]

      :else
      [application-list/component
       (-> (application-list-defaults)
           (update :visible-columns disj :submitted)
           (assoc :id applications
                  :applications applications))])))

(defn actions-page []
  [:div
   [document-title (text :t.navigation/actions)]
   [:div.spaced-sections
    [collapsible/component
     {:id "todo-applications"
      :open? true
      :title (text :t.actions/todo-applications)
      :collapse [:<>
                 [:label
                  "Search "
                  [:input {:type :text
                           :default-value ""
                           :on-change (fn [event]
                                        (let [query (-> event .-target .-value)]
                                          (rf/dispatch [::fetch-todo-applications query])))}]]
                 [todo-applications]]}]
    [collapsible/component
     {:id "handled-applications"
      :on-open #(rf/dispatch [::fetch-handled-applications])
      :title (text :t.actions/handled-applications)
      :collapse [:<>
                 [:label
                  "Search "
                  [:input {:type :text
                           :default-value ""
                           :on-change (fn [event]
                                        (let [query (-> event .-target .-value)]
                                          (rf/dispatch [::fetch-handled-applications query])))}]]
                 [handled-applications]]}]]])
