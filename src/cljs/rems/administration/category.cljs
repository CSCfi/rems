(ns rems.administration.category
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ category-id]]
   {:dispatch [::fetch-category category-id]}))

(rf/reg-event-fx
 ::fetch-category
 (fn [{:keys [db]} [_ category-id]]
   (fetch (str "/api/categories/" category-id)
          {:handler #(rf/dispatch [::fetch-category-result %])
           :error-handler (flash-message/default-error-handler :top "Fetch category")})
   {:db (assoc db ::loading? true)}))

(rf/reg-event-fx
 ::fetch-category-result
 (fn [{:keys [db]} [_ category]]
   {:db (-> db
            (assoc ::category category)
            (dissoc ::loading?))}))

(rf/reg-sub ::category (fn [db _] (::category db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn category-view [category]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "category"
     :title [:span (:en (:title (js->clj (. js/JSON (parse (get-in category [:data]))) :keywordize-keys true)))]
     :always [:div
              ;; [inline-info-field (text :t.administration/organization) (get-in resource [:organization :organization/name language])]
              [inline-info-field (text :t.administration/category-id) (:id category)]
              [inline-info-field (text :t.administration/category-name) (:en (:title (js->clj (. js/JSON (parse (get-in category [:data]))) :keywordize-keys true)))]
              [inline-info-field (text :t.administration/category-organization) (str (get-in category [:organization :organization/name :en]))] ;;:category-organization
              ;; [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? resource)}]]
              ]}]
;;    [licenses-view (:licenses resource) language]
;;    [resource-blacklist]
;; (let [id (:id resource)]
;;       [:div.col.commands
;;        [administration/back-button "/administration/resources"]
;;        [roles/show-when roles/+admin-write-roles+
;;         [status-flags/enabled-toggle resource #(rf/dispatch [:rems.administration.resources/set-resource-enabled %1 %2 [::enter-page id]])]
;;         [status-flags/archived-toggle resource #(rf/dispatch [:rems.administration.resources/set-resource-archived %1 %2 [::enter-page id]])]]])
   ])

(defn category-page []
  (let [category (rf/subscribe [::category])
        loading? (rf/subscribe [::loading?])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/category)]
     [flash-message/component :top]
     (if @loading?
       [spinner/big]
       [category-view
        @category])]))
