(ns rems.administration.workflows
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.status-flags :as status-flags]
            [rems.administration.workflow :as workflow]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [put! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch-workflows]
                 [:rems.table/reset]
                 [:rems.administration.administration/remember-current-page]]}))

(rf/reg-event-db
 ::fetch-workflows
 (fn [db]
   (let [description [text :t.administration/workflows]]
     (fetch "/api/workflows"
            {:url-params {:disabled true
                          :archived (status-flags/display-archived? db)}
             :handler #(rf/dispatch [::fetch-workflows-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
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
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(rf/reg-event-fx
 ::set-workflow-enabled
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/workflows/enabled"
         {:params (select-keys item [:id :enabled])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(defn- to-create-workflow []
  [atoms/link {:class "btn btn-primary" :id :create-workflow}
   "/administration/workflows/create"
   (text :t.administration/create-workflow)])

(defn- to-view-workflow [workflow-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/workflows/" workflow-id)
   (text :t.administration/view)])

(defn- modify-workflow-dropdown [workflow]
  [atoms/commands-group-button
   {:label (text :t.actions/modify)}
   (when (roles/can-modify-organization-item? workflow)
     (list
      (workflow/edit-action (:id workflow))
      (status-flags/enabled-toggle-action {:on-change #(rf/dispatch [::set-workflow-enabled %1 %2 [::fetch-workflows]])} workflow)
      (status-flags/archived-toggle-action {:on-change #(rf/dispatch [::set-workflow-archived %1 %2 [::fetch-workflows]])} workflow)))])

(rf/reg-sub
 ::workflows-table-rows
 (fn [_ _]
   [(rf/subscribe [::workflows])
    (rf/subscribe [:language])])
 (fn [[workflows language] _]
   (map (fn [workflow]
          {:key (:id workflow)
           :organization {:value (get-in workflow [:organization :organization/short-name language])}
           :title {:value (:title workflow)}
           :active (let [checked? (status-flags/active? workflow)]
                     {:display-value [readonly-checkbox {:value checked?}]
                      :sort-value (if checked? 1 2)})
           :commands {:display-value [:div.commands
                                      [to-view-workflow (:id workflow)]
                                      [modify-workflow-dropdown workflow]]}})
        workflows)))

(defn- workflows-list []
  (let [workflows-table {:id ::workflows
                         :columns [{:key :organization
                                    :title (text :t.administration/organization)}
                                   {:key :title
                                    :title (text :t.administration/title)}
                                   {:key :active
                                    :title (text :t.administration/active)
                                    :filterable? false}
                                   {:key :commands
                                    :sortable? false
                                    :filterable? false
                                    :aria-label (text :t.actions/commands)}]
                         :rows [::workflows-table-rows]
                         :default-sort-column :title}]
    [:div.mt-3
     [table/search workflows-table]
     [table/table workflows-table]]))

;; TODO Very similar components are used in here, licenses, forms, resources
(defn workflows-page []
  (into [:div
         [administration/navigator]
         [document-title (text :t.administration/workflows)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[roles/show-when roles/+admin-write-roles+
            [atoms/commands [to-create-workflow]]
            [status-flags/status-flags-intro #(rf/dispatch [::fetch-workflows])]]
           [workflows-list]])))
