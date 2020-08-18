(ns rems.administration.organization
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [document-title enrich-user enrich-email readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.flash-message :as flash-message]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ organization-id]]
   {:db (assoc db ::loading? true)
    ::fetch-organization [organization-id]}))

(defn- fetch-organization [organization-id]
  (fetch (str "/api/organizations/" organization-id)
         {:handler #(rf/dispatch [::fetch-organization-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch organization")}))

(rf/reg-fx ::fetch-organization (fn [[organization-id]] (fetch-organization organization-id)))

(rf/reg-event-db
 ::fetch-organization-result
 (fn [db [_ organization]]
   (-> db
       (assoc ::organization organization)
       (dissoc ::loading?))))

(rf/reg-sub ::organization (fn [db _] (::organization db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn edit-button [id]
  [atoms/link {:class "btn btn-primary edit-organization"}
   (str "/administration/organizations/edit/" id)
   (text :t.administration/edit)])

(defn organization-view [organization language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "organization"
     :title (get-in organization [:organization/name language])
     :always [:div
              [inline-info-field (text :t.administration/id) (:organization/id organization)]
              (for [[langcode localization] (:organization/name organization)]
                [inline-info-field (str (text :t.administration/title)
                                        " (" (str/upper-case (name langcode)) ")")
                 localization])
              [inline-info-field (text :t.administration/owners) (->> (:organization/owners organization)
                                                                      (map enrich-user)
                                                                      (map :display)
                                                                      (interpose [:br]))]
              [inline-info-field (text :t.administration/review-emails) (->> (:organization/review-emails organization)
                                                                             (map enrich-email)
                                                                             (map :display)
                                                                             (interpose [:br]))]
              [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? organization)}]]
              [inline-info-field (text :t.administration/last-modified) (localize-time (:organization/last-modified organization))]
              [inline-info-field (text :t.administration/modifier) (:organization/modifer organization)]]}]
   (let [id (:organization/id organization)]
     [:div.col.commands
      [administration/back-button "/administration/organizations"]
      [roles/when roles/show-admin-edit-buttons?
       #_[edit-button id] ; TODO hidden until implemented
       [status-flags/enabled-toggle organization #(rf/dispatch [:rems.administration.organizations/set-organization-enabled %1 %2 [::enter-page id]])]
       [status-flags/archived-toggle organization #(rf/dispatch [:rems.administration.organizations/set-organization-archived %1 %2 [::enter-page id]])]]])])

(defn organization-page []
  (let [organization (rf/subscribe [::organization])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration/navigator]
       [document-title (text :t.administration/organization)]
       [flash-message/component :top]
       (if @loading?
         [spinner/big]
         [organization-view @organization @language])])))
