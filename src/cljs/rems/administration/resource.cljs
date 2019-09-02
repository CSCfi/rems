(ns rems.administration.resource
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.license :refer [licenses-view]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ resource-id]]
   {:db (assoc db ::loading? true)
    ::fetch-resource [resource-id]}))

(defn- fetch-resource [resource-id]
  (fetch (str "/api/resources/" resource-id)
         {:handler #(rf/dispatch [::fetch-resource-result %])}))

(rf/reg-fx ::fetch-resource (fn [[resource-id]] (fetch-resource resource-id)))

(rf/reg-event-db
 ::fetch-resource-result
 (fn [db [_ resource]]
   (-> db
       (assoc ::resource resource)
       (dissoc ::loading?))))

(rf/reg-sub ::resource (fn [db _] (::resource db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- back-button []
  [atoms/link {:class "btn btn-secondary"}
   "/#/administration/resources"
   (text :t.administration/back)])

(defn resource-view [resource language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "resource"
     :title [:span (andstr (:domain resource) "/") (:resid resource)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (:organization resource)]
              [inline-info-field (text :t.administration/resource) (:resid resource)]
              [inline-info-field (text :t.administration/start) (localize-time (:start resource))]
              [inline-info-field (text :t.administration/end) (localize-time (:end resource))]
              [inline-info-field (text :t.administration/active) [readonly-checkbox (status-flags/active? resource)]]]}]
   [licenses-view (:licenses resource) language]
   (let [id (:id resource)]
     [:div.col.commands
      [back-button]
      [status-flags/enabled-toggle resource #(rf/dispatch [:rems.administration.resources/update-resource %1 %2 [::enter-page id]])]
      [status-flags/archived-toggle resource #(rf/dispatch [:rems.administration.resources/update-resource %1 %2 [::enter-page id]])]])])

(defn resource-page []
  (let [resource (rf/subscribe [::resource])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [document-title (text :t.administration/resource)]
       [flash-message/component]
       (if @loading?
         [spinner/big]
         [resource-view @resource @language])])))
