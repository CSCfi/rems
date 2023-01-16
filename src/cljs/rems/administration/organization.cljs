(ns rems.administration.organization
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [document-title enrich-user readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.common.roles :as roles]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
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
  [atoms/link {:id "edit-organization"
               :class "btn btn-primary"}
   (str "/administration/organizations/edit/" id)
   (text :t.administration/edit)])

(defn- display-localized-review-email [review-email]
  [:div
   (doall (for [[langcode localization] (:name review-email)]
            ^{:key (str "review-email-" (name langcode))}
            [inline-info-field (str (text :t.administration/name)
                                    " (" (str/upper-case (name langcode)) ")")
             localization]))
   [inline-info-field (text :t.administration/email) (:email review-email)]])

(defn- review-emails-field [review-emails]
  (if (empty? review-emails)
    [inline-info-field (text :t.administration/review-emails)] ; looks good when empty
    [:div.mb-2
     [:label (text :t.administration/review-emails)]
     [:div.solid-group
      (->> review-emails
           (map display-localized-review-email)
           (interpose [:br]))]]))

(defn organization-view [organization language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "organization"
     :title (get-in organization [:organization/name language])
     :always [:div
              [inline-info-field (text :t.administration/id) (:organization/id organization)]
              (doall (for [[langcode localization] (:organization/short-name organization)]
                       ^{:key (str "short-name-" (name langcode))}
                       [inline-info-field (str (text :t.administration/short-name)
                                               " (" (str/upper-case (name langcode)) ")")
                        localization]))
              (doall (for [[langcode localization] (:organization/name organization)]
                       ^{:key (str "name-" (name langcode))}
                       [inline-info-field (str (text :t.administration/title)
                                               " (" (str/upper-case (name langcode)) ")")
                        localization]))
              [inline-info-field
               (text :t.administration/owners)
               (->> (:organization/owners organization)
                    (map enrich-user)
                    (map :display))
               {:multiline? true}]
              [review-emails-field (:organization/review-emails organization)]
              [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? organization)}]]]}]
   (let [id (:organization/id organization)
         org-owner? (->> @(rf/subscribe [:owned-organizations])
                         (some (comp #{id} :organization/id)))
         set-org-enabled #(rf/dispatch [:rems.administration.organizations/set-organization-enabled %1 %2 [::enter-page id]])
         set-org-archived #(rf/dispatch [:rems.administration.organizations/set-organization-archived %1 %2 [::enter-page id]])]
     [:div.col.commands
      [administration/back-button "/administration/organizations"]
      (when org-owner?
        [edit-button id])
      [roles/show-when #{:owner}
       [status-flags/enabled-toggle {:id :enable-toggle} organization set-org-enabled]
       [status-flags/archived-toggle {:id :archive-toggle} organization set-org-archived]]])])

(defn organization-page []
  (let [organization (rf/subscribe [::organization])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/organization)]
     [flash-message/component :top]
     (if @loading?
       [spinner/big]
       [organization-view @organization @language])]))
