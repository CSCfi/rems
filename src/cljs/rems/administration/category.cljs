(ns rems.administration.category
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field localized-info-field]]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.common.util :refer [parse-int]]
            [rems.spinner :as spinner]
            [rems.text :refer [text localized]]
            [rems.util :refer [fetch navigate! post!]]))

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

(rf/reg-event-fx
 ::delete-category
 (fn [{:keys [db]} _]
   (let [description [text :t.administration/delete]
         category-id (parse-int (get-in db [::category :category/id]))]
     (post! "/api/categories/delete"
            {:params {:category/id category-id}
             :handler (flash-message/status-update-handler
                       :top description #(navigate! "/administration/categories"))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-sub ::category (fn [db _] (::category db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- category-link [category]
  [atoms/link nil
   (str "/administration/categories/" (:category/id category))
   (localized (:category/title category))])

(defn- to-edit-category [category-id]
  [atoms/link {:class "btn btn-primary edit-category"}
   (str "/administration/categories/edit/" category-id)
   (text :t.administration/edit)])

(defn- delete-category-button []
  [:button#delete.btn.btn-primary
   {:type :button
    :on-click #(when (js/confirm (text :t.administration/delete-confirmation))
                 (rf/dispatch [::delete-category]))}
   (text :t.administration/delete)])

(defn category-view []
  (let [category (rf/subscribe [::category])
        language (rf/subscribe [:language])]
    [:div.spaced-vertically-3
     [collapsible/component
      {:id "category"
       :title [:span (get-in @category [:category/title @language])]
       :always [:div
                [localized-info-field (:category/title @category) {:label (text :t.administration/title)}]
                [localized-info-field (:category/description @category) {:label (text :t.administration/description)}]
                [inline-info-field (text :t.administration/display-order) (:category/display-order @category)]
                [inline-info-field (text :t.administration/category-children)
                 (when-let [categories (:category/children @category)]
                   (doall (interpose ", " (for [cat categories]
                                            ^{:key {:category/id cat}}
                                            [category-link cat]))))]]}]
     [:div.col.commands
      [administration/back-button "/administration/categories"]
      [roles/show-when roles/+admin-write-roles+
       [delete-category-button]
       [to-edit-category (:category/id @category)]]]]))

(defn category-page []
  (let [loading? (rf/subscribe [::loading?])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/category)]
     [flash-message/component :top]
     (if @loading?
       [spinner/big]
       [category-view])]))
