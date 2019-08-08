(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title close-symbol]]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (-> db
            (dissoc ::todo-applications
                    ::handled-applications)
            (assoc ::initializing-todo-applications? true
                   ::initializing-handled-applications? true))
    :dispatch-n [[::fetch-todo-applications]
                 [:rems.table/reset]]}))

;;;; applications to do

(rf/reg-event-fx
 ::fetch-todo-applications
 (fn [{:keys [db]} [_ query]]
   ;; do only one fetch at a time - will retry after the pending fetch is finished
   (when-not (::fetching-todo-applications? db)
     (fetch "/api/applications/todo"
            {:url-params (when query
                           {:query query})
             :handler #(rf/dispatch [::fetch-todo-applications-result % query])}))
   {:db (assoc db
               ::todo-applications-query query
               ::fetching-todo-applications? true)}))

(rf/reg-event-db
 ::fetch-todo-applications-result
 (fn [db [_ result query]]
   ;; fetch again if the query that just finished was not the latest
   (when-not (= query (::todo-applications-query db))
     (rf/dispatch [::fetch-todo-applications (::todo-applications-query db)]))
   (-> db
       (assoc ::todo-applications result)
       (dissoc ::initializing-todo-applications?
               ::fetching-todo-applications?))))

(rf/reg-sub
 ::todo-applications
 (fn [db _]
   (::todo-applications db)))

(rf/reg-sub
 ::initializing-todo-applications?
 (fn [db _]
   (::initializing-todo-applications? db)))

(rf/reg-sub
 ::searching-todo-applications?
 (fn [db _]
   (and (::fetching-todo-applications? db)
        (not (::initializing-todo-applications? db)))))

;;;; handled applications

(rf/reg-event-fx
 ::fetch-handled-applications
 (fn [{:keys [db]} [_ query]]
   ;; do only one fetch at a time - will retry after the pending fetch is finished
   (when-not (::fetching-handled-applications? db)
     (fetch "/api/applications/handled"
            {:url-params (when query
                           {:query query})
             :handler #(rf/dispatch [::fetch-handled-applications-result % query])}))
   {:db (assoc db
               ::handled-applications-query query
               ::fetching-handled-applications? true)}))

(rf/reg-event-db
 ::fetch-handled-applications-result
 (fn [db [_ result query]]
   ;; fetch again if the query that just finished was not the latest
   (when-not (= query (::handled-applications-query db))
     (rf/dispatch [::fetch-handled-applications (::handled-applications-query db)]))
   (-> db
       (assoc ::handled-applications result)
       (dissoc ::initializing-handled-applications?
               ::fetching-handled-applications?))))

(rf/reg-sub
 ::handled-applications
 (fn [db _]
   (::handled-applications db)))

(rf/reg-sub
 ::initializing-handled-applications?
 (fn [db _]
   (::initializing-handled-applications? db)))

(rf/reg-sub
 ::searching-handled-applications?
 (fn [db _]
   (and (::fetching-handled-applications? db)
        (not (::initializing-handled-applications? db)))))

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
      @(rf/subscribe [::initializing-todo-applications?])
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
      @(rf/subscribe [::initializing-handled-applications?])
      [spinner/big]

      (empty? @(rf/subscribe [applications]))
      [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]

      :else
      [application-list/component
       (-> (application-list-defaults)
           (update :visible-columns disj :submitted)
           (assoc :id applications
                  :applications applications))])))

(defn- search-field [{:keys [id on-search searching?]}]
  (let [input-value (r/atom "")
        input-element (atom nil)]
    ;; TODO: localization & aria-labels
    (fn [{:keys [id on-search searching?]}]
      [:div.form-inline.mb-3
       [:div.form-group.mr-1
        [:label {:for id}
         "Search"]]

       [:div.input-group.mr-2
        [:input.form-control
         {:id id
          :type :text
          :value @input-value
          :ref (fn [element]
                 (reset! input-element element))
          :on-change (fn [event]
                       (let [value (-> event .-target .-value)]
                         (reset! input-value value)
                         (on-search value)))}]

        (when-not (= "" @input-value)
          [:div.input-group-append
           [:button.btn.btn-outline-secondary
            {:id (str id "-clear")
             :type :button
             ;; override the custom font-size from .btn which breaks .input-group
             :style {:font-size "inherit"}
             :on-click (fn []
                         (reset! input-value "")
                         (on-search "")
                         (.focus @input-element))}
            [close-symbol]]])]

       (when searching?
         [spinner/small])])))

(defn actions-page []
  [:div
   [document-title (text :t.navigation/actions)]
   [:div.spaced-sections
    [collapsible/component
     {:id "todo-applications"
      :open? true
      :title (text :t.actions/todo-applications)
      :collapse [:<>
                 [search-field {:id "todo-search"
                                :on-search #(rf/dispatch [::fetch-todo-applications %])
                                :searching? @(rf/subscribe [::searching-todo-applications?])}]
                 [todo-applications]]}]
    [collapsible/component
     {:id "handled-applications"
      :on-open #(rf/dispatch [::fetch-handled-applications])
      :title (text :t.actions/handled-applications)
      :collapse [:<>
                 [search-field {:id "handled-search"
                                :on-search #(rf/dispatch [::fetch-handled-applications %])
                                :searching? @(rf/subscribe [::searching-handled-applications?])}]
                 [handled-applications]]}]]])
