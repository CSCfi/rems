(ns rems.administration.licenses
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [get-localized-title text]]
            [rems.util :refer [put! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch-licenses]
                 [:rems.table/reset]
                 [:rems.administration.administration/remember-current-page]]}))

(rf/reg-event-db
 ::fetch-licenses
 (fn [db]
   (let [description [text :t.administration/licenses]]
     (fetch "/api/licenses"
            {:url-params {:disabled true
                          :archived (status-flags/display-archived? db)}
             :handler #(rf/dispatch [::fetch-licenses-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   (assoc db ::loading? true)))

(rf/reg-event-db
 ::fetch-licenses-result
 (fn [db [_ licenses]]
   (-> db
       (assoc ::licenses licenses)
       (dissoc ::loading?))))

(rf/reg-sub ::licenses (fn [db _] (::licenses db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::set-license-archived
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/licenses/archived"
         {:params (select-keys item [:id :archived])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(rf/reg-event-fx
 ::set-license-enabled
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/licenses/enabled"
         {:params (select-keys item [:id :enabled])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(defn- to-create-license []
  [atoms/link {:id :create-license
               :class "btn btn-primary"}
   "/administration/licenses/create"
   (text :t.administration/create-license)])

(defn- to-view-license [license-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/licenses/" license-id)
   (text :t.administration/view)])

(defn- modify-license-dropdown [license]
  [atoms/commands-group-button
   {:label (text :t.actions/modify)}
   (when (roles/can-modify-organization-item? license)
     (list
      (status-flags/enabled-toggle-action {:on-change  #(rf/dispatch [::set-license-enabled %1 %2 [::fetch-licenses]])} license)
      (status-flags/archived-toggle-action {:on-change #(rf/dispatch [::set-license-archived %1 %2 [::fetch-licenses]])} license)))])

(rf/reg-sub
 ::licenses-table-rows
 (fn [_ _]
   [(rf/subscribe [::licenses])
    (rf/subscribe [:language])])
 (fn [[licenses language] _]
   (map (fn [license]
          {:key (:id license)
           :title {:value (get-localized-title license language)}
           :type {:value (:licensetype license)}
           :organization {:value (get-in license [:organization :organization/short-name language])}
           :active (let [checked? (status-flags/active? license)]
                     {:display-value [readonly-checkbox {:value checked?}]
                      :sort-value (if checked? 1 2)})
           :commands {:display-value [:div.commands
                                      [to-view-license (:id license)]
                                      [modify-license-dropdown license]]}})
        licenses)))

(defn- licenses-list []
  (let [licenses-table {:id ::licenses
                        :columns [{:key :organization
                                   :title (text :t.administration/organization)}
                                  {:key :title
                                   :title (text :t.administration/title)}
                                  {:key :type
                                   :title (text :t.administration/type)}
                                  {:key :active
                                   :title (text :t.administration/active)
                                   :filterable? false}
                                  {:key :commands
                                   :sortable? false
                                   :filterable? false
                                   :aria-label (text :t.actions/commands)}]
                        :rows [::licenses-table-rows]
                        :default-sort-column :title}]
    [:div.mt-3
     [table/search licenses-table]
     [table/table licenses-table]]))

(defn licenses-page []
  (into [:div
         [administration/navigator]
         [document-title (text :t.administration/licenses)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[roles/show-when roles/+admin-write-roles+
            [atoms/commands [to-create-license]]
            [status-flags/status-flags-intro #(rf/dispatch [::fetch-licenses])]]
           [licenses-list]])))
