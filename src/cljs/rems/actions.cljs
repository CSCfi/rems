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
                    ::handled-applications))
    :dispatch-n [[::todo-applications]
                 [:rems.table/reset]]}))

(defn reg-fetcher [id url]
  (let [result-id (keyword (namespace id)
                           (str (name id) "-result"))]
    (rf/reg-event-fx
     id
     (fn [{:keys [db]} [_ query]]
       ;; do only one fetch at a time - will retry after the pending fetch is finished
       (when-not (get-in db [id :fetching?])
         (fetch url
                {:url-params (when query
                               {:query query})
                 :handler #(rf/dispatch [result-id % query])
                 ;; TODO: show error message
                 :error-handler #(rf/dispatch [result-id nil query])}))
       {:db (-> db
                (assoc-in [id :query] query)
                (assoc-in [id :fetching?] true))}))

    (rf/reg-event-db
     result-id
     (fn [db [_ result query]]
       ;; fetch again if the query that just finished was not the latest
       (let [latest-query (get-in db [id :query])]
         (when-not (= query latest-query)
           (rf/dispatch [id latest-query])))
       (-> db
           (assoc-in [id :data] result)
           (assoc-in [id :initialized?] true)
           (assoc-in [id :fetching?] false))))

    (rf/reg-sub
     id
     (fn [db [_ k]]
       (case k
         :searching? (and (get-in db [id :fetching?])
                          (get-in db [id :initialized?]))
         (get-in db [id (or k :data)]))))))

(reg-fetcher ::todo-applications "/api/applications/todo")
(reg-fetcher ::handled-applications "/api/applications/handled")

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
     :default-sort-order :desc
     :filterable? false}))

(defn- todo-applications []
  (let [applications ::todo-applications]
    (cond
      (not @(rf/subscribe [::todo-applications :initialized?]))
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
      (not @(rf/subscribe [::handled-applications :initialized?]))
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
      [:div.search-field.mb-3
       [:label.mr-1 {:for id}
        "Search"]

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
                                :on-search #(rf/dispatch [::todo-applications %])
                                :searching? @(rf/subscribe [::todo-applications :searching?])}]
                 [todo-applications]]}]
    [collapsible/component
     {:id "handled-applications"
      :on-open #(rf/dispatch [::handled-applications])
      :title (text :t.actions/handled-applications)
      :collapse [:<>
                 [search-field {:id "handled-search"
                                :on-search #(rf/dispatch [::handled-applications %])
                                :searching? @(rf/subscribe [::handled-applications :searching?])}]
                 [handled-applications]]}]]])
