(ns rems.administration.organizations
  "Admin page for organizations."
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [put! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [_db]}]
   {:dispatch-n [[::fetch-organizations]
                 [:rems.administration.administration/remember-current-page]]}))

(rf/reg-event-db
 ::fetch-organizations
 (fn [db]
   (let [description [text :t.administration/organizations]]
     (fetch "/api/organizations"
            {:url-params {:disabled true
                          :archived (status-flags/display-archived? db)}
             :handler #(rf/dispatch [::fetch-organizations-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   (assoc db ::loading? true)))

(rf/reg-event-db
 ::fetch-organizations-result
 (fn [db [_ organizations]]
   (-> db
       (assoc ::organizations organizations)
       (dissoc ::loading?))))

(rf/reg-sub ::organizations (fn [db _] (::organizations db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::set-organization-archived
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/organizations/archived"
         {:params (select-keys item [:organization/id :archived])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(rf/reg-event-fx
 ::set-organization-enabled
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/organizations/enabled"
         {:params (select-keys item [:organization/id :enabled])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(defn- to-create-organization []
  [atoms/link {:id :create-organization
               :class "btn btn-primary"}
   "/administration/organizations/create"
   (text :t.administration/create-organization)])

(defn- to-view-organization [organization-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/organizations/" organization-id)
   (text :t.administration/view)])

(rf/reg-sub
 ::organizations-table-rows
 (fn [_ _]
   [(rf/subscribe [::organizations])
    (rf/subscribe [:language])])
 (fn [[organizations language] _]
   (for [organization organizations]
     {:key (:organization/id organization)
      :short-name {:value (get-in organization [:organization/short-name language])}
      :name {:value (get-in organization [:organization/name language])}
      :active (let [checked? (status-flags/active? organization)]
                {:td [:td.active
                      [readonly-checkbox {:value checked?}]]
                 :sort-value (if checked? 1 2)})
      :commands {:td [:td.commands
                      [to-view-organization (:organization/id organization)]
                      [roles/when roles/+admin-write-roles+ ;; TODO doesn't match API roles exactly
                       [status-flags/enabled-toggle organization #(rf/dispatch [::set-organization-enabled %1 %2 [::fetch-organizations]])]
                       [status-flags/archived-toggle organization #(rf/dispatch [::set-organization-archived %1 %2 [::fetch-organizations]])]]]}})))

(defn- organizations-list []
  (let [organizations-table {:id ::organizations
                             :columns [{:key :short-name
                                        :title (text :t.administration/short-name)}
                                       {:key :name
                                        :title (text :t.administration/organization)}
                                       {:key :active
                                        :title (text :t.administration/active)
                                        :filterable? false}
                                       {:key :commands
                                        :sortable? false
                                        :filterable? false}]
                             :rows [::organizations-table-rows]
                             :default-sort-column :name}]
    [:div.mt-3
     [table/search organizations-table]
     [table/table organizations-table]]))

(defn organizations-page []
  (into [:div
         [administration/navigator]
         [document-title (text :t.administration/organizations)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[roles/when roles/+admin-write-roles+ ;; TODO doesn't match API roles exactly
            [to-create-organization]
            [status-flags/display-archived-toggle #(rf/dispatch [::fetch-organizations])]
            [status-flags/disabled-and-archived-explanation]]
           [organizations-list]])))
