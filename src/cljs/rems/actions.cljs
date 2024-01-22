(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.search :as search]
            [rems.text :refer [text text-format text-format-map]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db
                ::todo-applications
                ::handled-applications
                ::handled-applications-count
                ::show-all-rows)
    :dispatch-n [[::todo-applications]
                 [::handled-applications-count]
                 [:rems.table/reset]]}))

(fetcher/reg-fetcher ::todo-applications "/api/applications/todo")
(fetcher/reg-fetcher ::handled-applications "/api/applications/handled")
(fetcher/reg-fetcher ::handled-applications-count "/api/applications/handled/count")

(rf/reg-sub ::show-all-rows (fn [db _] (::show-all-rows db)))
(rf/reg-event-db ::set-show-all-rows (fn [db [_ value]] (assoc db ::show-all-rows value)))

;;;; UI

(defn actions-page []
  (let [query (:query @(rf/subscribe [::handled-applications :query]))
        show-all-rows? @(rf/subscribe [::show-all-rows])
        handled-count @(rf/subscribe [::handled-applications-count :data])]
    [:div
     [document-title (text :t.navigation/actions)]
     [flash-message/component :top]
     [:div.spaced-sections
      [collapsible/component
       {:id "todo-applications-collapse"
        :open? true
        :title (text :t.actions/todo-applications)
        :bottom-less-button? false
        :always [:div (text-format-map :t.actions/todo-applications-count {:count (some-> @(rf/subscribe [::todo-applications :data]) count)})]
        :collapse [:<>
                   [search/application-search-field {:id "todo-search"
                                                     :on-search #(rf/dispatch [::todo-applications {:query %}])
                                                     :searching? @(rf/subscribe [::todo-applications :searching?])}]
                   [application-list/component {:applications ::todo-applications
                                                :hidden-columns #{:state :created}
                                                :default-sort-column :last-activity
                                                :default-sort-order :desc}]]}]
      [collapsible/component
       {:id "handled-applications-collapse"
        :title (text :t.actions/handled-applications)
        :on-open #(do (rf/dispatch [::handled-applications {:limit 51}])
                      (rf/dispatch [::handled-applications-count]))
        :top-less-button? false
        :bottom-less-button? false
        :always (when handled-count [:div [:p (text-format-map :t.actions/handled-applications-count {:count handled-count})]])
        :collapse (when (and handled-count (pos? handled-count)) ; is there anything to show?
                    [:<>
                     [search/application-search-field {:id "handled-search"
                                                       :on-search #(do (rf/dispatch [::set-show-all-rows false])
                                                                       (rf/dispatch [::handled-applications {:query % :limit 51}]))
                                                       :searching? @(rf/subscribe [::handled-applications :searching?])
                                                       :debounce-time 2000}]

                     ;; XXX: it would be nice to extract this as a pattern
                     (when (and (not show-all-rows?)
                                (> (count @(rf/subscribe [::handled-applications :data])) 50))
                       [:p.my-3.alert.alert-info
                        (text-format :t.table/first-rows-only 50)
                        [:button.btn.btn-secondary.ml-3 {:on-click #(do (rf/dispatch [::set-show-all-rows true])
                                                                        (rf/dispatch [::handled-applications (when query {:query query})]))}
                         (text :t.table/show-all-rows)]])

                     (when @(rf/subscribe [::handled-applications :data?])
                       [application-list/component {:applications ::handled-applications
                                                    :hidden-columns #{:todo :created :submitted}
                                                    :default-sort-column :last-activity
                                                    :default-sort-order :desc
                                                    :max-rows (when-not show-all-rows? 50)}])])}]]]))
