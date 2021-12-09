(ns rems.administration.category
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text localized]]
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

(defn- category-link [category]
  [atoms/link nil
   (str "/administration/categories/" (:category/id category))
   (or (localized (:category/title category))
       (text :t.missing))])

(defn category-view []
  (let [category (rf/subscribe [::category])
        language (rf/subscribe [:language])]
    [:div.spaced-vertically-3
     [collapsible/component
      {:id "category"
       :title [:span (get-in @category [:category/title @language])]
       :always [:div
                [inline-info-field (text :t.administration/category-description)
                 (localized (:category/description @category))]
                [inline-info-field (text :t.administration/category-children)
                 (when-let [categories (:category/children @category)]
                   (interpose ", " (map category-link categories)))]]}]
     [:div.col.commands
      [administration/back-button "/administration/categories"]]]))

(defn category-page []
  (let [loading? (rf/subscribe [::loading?])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/category)]
     [flash-message/component :top]
     (if @loading?
       [spinner/big]
       [category-view])]))
