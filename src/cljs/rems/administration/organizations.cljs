(ns rems.administration.organizations
  "Admin page for organizations."
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.organization :refer [edit-action]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.config :as config]
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
     ;; Refresh :organizations subscription. We do this here
     ;; since it would be surprising if an organization was visible on
     ;; this page but not selectable e.g. when creating a resource.
     (config/fetch-organizations!)
     ;; Fetch the organizations for our display on this page:
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

(defn modify-organization-dropdown [organization]
  (let [id (:organization/id organization)
        org-owner? (->> @(rf/subscribe [:owned-organizations])
                        (some (comp #{id} :organization/id)))]
    [atoms/commands-group-button
     {:label (text :t.actions/modify)}

     (when org-owner?
       (edit-action id))

     ;; XXX: organization owner cannot use these actions currently
     (when (roles/has-roles? :owner)
       (list
        (status-flags/enabled-toggle-action {:on-change #(rf/dispatch [::set-organization-enabled %1 %2 [::fetch-organizations]])} organization)
        (status-flags/archived-toggle-action {:on-change #(rf/dispatch [::set-organization-archived %1 %2 [::fetch-organizations]])} organization)))]))

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
                {:display-value [readonly-checkbox {:value checked?}]
                 :sort-value (if checked? 1 2)})
      :commands {:display-value [:div.commands
                                 [to-view-organization (:organization/id organization)]
                                 [modify-organization-dropdown organization]]}})))

(defn- organizations-list []
  (let [organizations-table {:id ::organizations
                             :columns [{:key :short-name
                                        :title (text :t.administration/short-name)}
                                       {:key :name
                                        :title (text :t.administration/title)}
                                       {:key :active
                                        :title (text :t.administration/active)
                                        :filterable? false}
                                       {:key :commands
                                        :sortable? false
                                        :filterable? false
                                        :aria-label (text :t.actions/commands)}]
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
          [[roles/show-when #{:owner}
            [atoms/commands [to-create-organization]]
            [status-flags/status-flags-intro #(rf/dispatch [::fetch-organizations])]]
           [organizations-list]])))
