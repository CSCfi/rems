(ns rems.applications
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
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

(fetcher/reg-fetcher ::my-applications "/api/my-applications")
(fetcher/reg-fetcher ::all-applications "/api/applications")

;;;; UI

(defn applications-page []
  (let [identity @(rf/subscribe [:identity])]
    [:div
     [document-title (text :t.applications/applications)]
     [flash-message/component :top]
     (if (not @(rf/subscribe [::my-applications :initialized?]))
       [spinner/big]
       [:<>
        (when (roles/show-all-applications? (:roles identity))
          [:h2 (text :t.applications/my-applications)])
        [search/application-search-field {:id "my-applications-search"
                                          :on-search #(rf/dispatch [::my-applications {:query %}])
                                          :searching? @(rf/subscribe [::my-applications :searching?])}]
        [application-list/component {:applications ::my-applications
                                     :hidden-columns #{:applicant :handlers :todo}
                                     :default-sort-column :created
                                     :default-sort-order :desc}]
        (when (roles/show-all-applications? (:roles identity))
          [:<>
           [:h2 (text :t.applications/all-applications)]
           [search/application-search-field {:id "all-applications-search"
                                             :on-search #(rf/dispatch [::all-applications {:query %}])
                                             :searching? @(rf/subscribe [::all-applications :searching?])}]
           [application-list/component {:applications ::all-applications
                                        :hidden-columns #{:handlers :todo :created :submitted}
                                        :default-sort-column :last-activity
                                        :default-sort-order :desc}]])])]))
