(ns rems.administration.resources
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch-resources]
                 [:rems.table/reset]
                 [:rems.administration.administration/remember-current-page]]}))

(rf/reg-event-fx
 ::fetch-resources
 (fn [{:keys [db]}]
   (let [description [text :t.administration/resources]]
     (fetch "/api/resources"
            {:url-params {:disabled true
                          :archived (status-flags/display-archived? db)}
             :handler #(rf/dispatch [::fetch-resources-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-resources-result
 (fn [db [_ resources]]
   (-> db
       (assoc ::resources resources)
       (dissoc ::loading?))))

(rf/reg-sub ::resources (fn [db _] (::resources db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::set-resource-archived
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/resources/archived"
         {:params (select-keys item [:id :archived])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(rf/reg-event-fx
 ::set-resource-enabled
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/resources/enabled"
         {:params (select-keys item [:id :enabled])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(defn- to-create-resource []
  [atoms/link {:id "create-resource"
               :class "btn btn-primary"}
   "/administration/resources/create"
   (text :t.administration/create-resource)])

(defn- to-view-resource [resource-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/resources/" resource-id)
   (text :t.administration/view)])

(defn- modify-resource-dropdown [resource]
  [atoms/commands-group-button
   (when (roles/can-modify-organization-item? resource)
     (list
      (status-flags/enabled-toggle-action {:on-change #(rf/dispatch [::set-resource-enabled %1 %2 [::fetch-resources]])} resource)
      (status-flags/archived-toggle-action {:on-change #(rf/dispatch [::set-resource-archived %1 %2 [::fetch-resources]])} resource)))])

(rf/reg-sub
 ::resources-table-rows
 (fn [_ _]
   [(rf/subscribe [::resources])
    (rf/subscribe [:language])])
 (fn [[resources language] _]
   (map (fn [resource]
          {:key (:id resource)
           :organization {:value (get-in resource [:organization :organization/short-name language])}
           :title {:value (:resid resource)}
           :active (let [checked? (status-flags/active? resource)]
                     {:display-value [readonly-checkbox {:value checked?}]
                      :sort-value (if checked? 1 2)})
           :commands {:display-value [:div.commands
                                      [to-view-resource (:id resource)]
                                      [modify-resource-dropdown resource]]}})
        resources)))

(defn- resources-list []
  (let [resources-table {:id ::resources
                         :columns [{:key :organization
                                    :title (text :t.administration/organization)}
                                   {:key :title
                                    :title (text :t.administration/resource)}
                                   {:key :active
                                    :title (text :t.administration/active)
                                    :filterable? false}
                                   {:key :commands
                                    :sortable? false
                                    :filterable? false
                                    :aria-label (text :t.actions/commands)}]
                         :rows [::resources-table-rows]
                         :default-sort-column :title}]
    [:div.mt-3
     [table/search resources-table]
     [table/table resources-table]]))

(defn resources-page []
  (into [:div
         [administration/navigator]
         [document-title (text :t.administration/resources)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[roles/show-when roles/+admin-write-roles+
            [atoms/commands [to-create-resource]]
            [status-flags/status-flags-intro  #(rf/dispatch [::fetch-resources])]]
           [resources-list]])))
