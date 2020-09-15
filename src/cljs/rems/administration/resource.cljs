(ns rems.administration.resource
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.blacklist :as blacklist]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.license :refer [licenses-view]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ resource-id]]
   {:dispatch [::fetch-resource resource-id]}))

(rf/reg-event-fx
 ::fetch-resource
 (fn [{:keys [db]} [_ resource-id]]
   (fetch (str "/api/resources/" resource-id)
          {:handler #(rf/dispatch [::fetch-resource-result %])
           :error-handler (flash-message/default-error-handler :top "Fetch resource")})
   {:db (assoc db ::loading? true)}))

(rf/reg-event-fx
 ::fetch-resource-result
 (fn [{:keys [db]} [_ resource]]
   {:db (-> db
            (assoc ::resource resource)
            (dissoc ::loading?))
    :dispatch-n [[::blacklist/fetch-blacklist {:resource (:resid resource)}]
                 [::blacklist/fetch-users]]}))

(rf/reg-sub ::resource (fn [db _] (::resource db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn resource-blacklist []
  [collapsible/component
   {:id "blacklist"
    :title (text :t.administration/blacklist)
    :always [:div
             [blacklist/blacklist]
             [blacklist/add-user-form {:resource/ext-id (:resid @(rf/subscribe [::resource]))}]]}])

(defn resource-view [resource language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "resource"
     :title [:span (andstr (get-in resource [:organization :organization/short-name language]) "/") (:resid resource)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (get-in resource [:organization :organization/name language])]
              [inline-info-field (text :t.administration/resource) (:resid resource)]
              [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? resource)}]]]}]
   [licenses-view (:licenses resource) language]
   [resource-blacklist]
   (let [id (:id resource)]
     [:div.col.commands
      [administration/back-button "/administration/resources"]
      [roles/when roles/show-admin-edit-buttons?
       [status-flags/enabled-toggle resource #(rf/dispatch [:rems.administration.resources/set-resource-enabled %1 %2 [::enter-page id]])]
       [status-flags/archived-toggle resource #(rf/dispatch [:rems.administration.resources/set-resource-archived %1 %2 [::enter-page id]])]]])])

(defn resource-page []
  (let [resource (rf/subscribe [::resource])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/resource)]
     [flash-message/component :top]
     (if @loading?
       [spinner/big]
       [resource-view @resource @language])]))
