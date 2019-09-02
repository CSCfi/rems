(ns rems.administration.workflows
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.administration.workflow :as workflow]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [put! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch-n [[::fetch-workflows]
                 [:rems.table/reset]]}))

(rf/reg-event-db
 ::fetch-workflows
 (fn [db]
   (fetch "/api/workflows/" {:url-params {:disabled true
                                          :archived (::display-old? db)
                                          :expired (::display-old? db)}
                             :handler #(rf/dispatch [::fetch-workflows-result %])})
   (assoc db ::loading? true)))

(rf/reg-event-db
 ::fetch-workflows-result
 (fn [db [_ workflows]]
   (-> db
       (assoc ::workflows workflows)
       (dissoc ::loading?))))

(rf/reg-sub ::workflows (fn [db _] (::workflows db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::set-workflow-archived
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/workflows/archived"
         {:params (select-keys item [:id :archived])
          :handler (flash-message/status-update-handler description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler description)})
   {}))

(rf/reg-event-fx
 ::set-workflow-enabled
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/workflows/enabled"
         {:params (select-keys item [:id :enabled])
          :handler (flash-message/status-update-handler description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler description)})
   {}))

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-workflows]}))
(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))

(defn- to-create-workflow []
  [atoms/link {:class "btn btn-primary"}
   "/#/administration/create-workflow"
   (text :t.administration/create-workflow)])

(defn- to-view-workflow [workflow-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/#/administration/workflows/" workflow-id)
   (text :t.administration/view)])

(rf/reg-sub
 ::workflows-table-rows
 (fn [_ _]
   [(rf/subscribe [::workflows])])
 (fn [[workflows] _]
   (map (fn [workflow]
          {:key (:id workflow)
           :organization {:value (:organization workflow)}
           :title {:value (:title workflow)}
           :start (let [value (:start workflow)]
                    {:value value
                     :display-value (localize-time value)})
           :end (let [value (:end workflow)]
                  {:value value
                   :display-value (localize-time value)})
           :active (let [checked? (status-flags/active? workflow)]
                     {:td [:td.active
                           [readonly-checkbox checked?]]
                      :sort-value (if checked? 1 2)})
           :commands {:td [:td.commands
                           [to-view-workflow (:id workflow)]
                           [workflow/edit-button (:id workflow)]
                           [status-flags/enabled-toggle workflow #(rf/dispatch [::set-workflow-enabled %1 %2 [::fetch-workflows]])]
                           [status-flags/archived-toggle workflow #(rf/dispatch [::set-workflow-archived %1 %2 [::fetch-workflows]])]]}})
        workflows)))

(defn- workflows-list []
  (let [workflows-table {:id ::workflows
                         :columns [{:key :organization
                                    :title (text :t.administration/organization)}
                                   {:key :title
                                    :title (text :t.administration/workflow)}
                                   {:key :start
                                    :title (text :t.administration/created)}
                                   {:key :end
                                    :title (text :t.administration/end)}
                                   {:key :active
                                    :title (text :t.administration/active)
                                    :filterable? false}
                                   {:key :commands
                                    :sortable? false
                                    :filterable? false}]
                         :rows [::workflows-table-rows]
                         :default-sort-column :title}]
    [:div.mt-3
     [table/search workflows-table]
     [table/table workflows-table]]))

;; TODO Very similar components are used in here, licenses, forms, resources
(defn workflows-page []
  (into [:div
         [administration-navigator-container]
         [document-title (text :t.administration/workflows)]
         [flash-message/component]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-workflow]
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [workflows-list]])))
