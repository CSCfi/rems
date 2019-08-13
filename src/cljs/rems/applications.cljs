(ns rems.applications
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
            [rems.roles :as roles]
            [rems.search :as search]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db
                ::my-applications
                ::all-applications)
    :dispatch-n [[::my-applications]
                 (when (roles/show-all-applications? (:roles (:identity db)))
                   [::all-applications])
                 [:rems.table/reset]]}))

(search/reg-fetcher ::my-applications "/api/my-applications")
(search/reg-fetcher ::all-applications "/api/applications")

;;;; UI

(defn- application-list-defaults []
  (let [config @(rf/subscribe [:rems.config/config])
        id-column (get config :application-id-column :id)]
    {:visible-columns #{id-column :description :resource :state :created :submitted :last-activity :view}
     :default-sort-column :created
     :default-sort-order :desc
     :filterable? false}))

;; TODO: deduplicate with rems.actions
(defn- application-list [applications]
  (cond
    (not @(rf/subscribe [applications :initialized?]))
    [spinner/big]

    (empty? @(rf/subscribe [applications]))
    [:div.applications.alert.alert-success (text :t.applications/empty)]

    :else
    [application-list/component
     (-> (application-list-defaults)
         (assoc :id applications
                :applications applications))]))

(defn applications-page []
  (let [identity @(rf/subscribe [:identity])]
    [:<>
     [document-title (text :t.applications/applications)]
     (when (roles/show-all-applications? (:roles identity))
       [:h2 (text :t.applications/my-applications)])
     [search/search-field {:id "my-applications-search"
                           :on-search #(rf/dispatch [::my-applications %])
                           :searching? @(rf/subscribe [::my-applications :searching?])}]
     [application-list ::my-applications]
     (when (roles/show-all-applications? (:roles identity))
       [:<>
        [:h2 (text :t.applications/all-applications)]
        [search/search-field {:id "all-applications-search"
                              :on-search #(rf/dispatch [::all-applications %])
                              :searching? @(rf/subscribe [::all-applications :searching?])}]
        [application-list ::all-applications]])]))
