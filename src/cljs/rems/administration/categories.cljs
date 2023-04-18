(ns rems.administration.categories
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch-categories]
                 [:rems.table/reset]
                 [:rems.administration.administration/remember-current-page]]}))

(rf/reg-event-fx
 ::fetch-categories
 (fn [{:keys [db]}]
   (let [description [text :t.administration/categories]]
     (fetch "/api/categories"
            {:handler #(rf/dispatch [::fetch-categories-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-categories-result
 (fn [db [_ categories]]
   (-> db
       (assoc ::categories categories)
       (dissoc ::loading?))))

(rf/reg-sub ::categories (fn [db _] (::categories db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- to-create-category []
  [atoms/link {:id "create-category"
               :class "btn btn-primary"}
   "/administration/categories/create"
   (text :t.administration/create-category)])

(defn- to-edit-category [category-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/categories/edit/" category-id)
   (text :t.administration/edit)])

(defn- to-view-category [category-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/categories/" category-id)
   (text :t.administration/view)])

(rf/reg-sub
 ::categories-table-rows
 (fn [_ _]
   [(rf/subscribe [::categories])
    (rf/subscribe [:language])])
 (fn [[categories language] _]
   (map (fn [category]
          {:key (:category/id category)
           :display-order {:value (:category/display-order category)
                           :sort-value [(:category/display-order category 2147483647)  ; can't use Java Integer/MAX_VALUE here but anything that is not set is last
                                        (get-in category [:category/title language])]} ; secondary sort-key is the same in the catalogue
           :title {:value (get-in category [:category/title language])}
           :description {:value (get-in category [:category/description language])}
           :commands {:display-value [:div.commands
                                      [to-view-category (:category/id category)]
                                      [roles/show-when roles/+admin-write-roles+
                                       [to-edit-category (:category/id category)]]]}})
        categories)))

(defn- categories-list []
  (let [categories-table {:id ::categories
                          :columns [{:key :display-order
                                     :title (text :t.administration/display-order)}
                                    {:key :title
                                     :title (text :t.administration/category)}
                                    {:key :description
                                     :title (text :t.administration/description)}
                                    {:key :commands
                                     :sortable? false
                                     :filterable? false
                                     :aria-label (text :t.actions/commands)}]
                          :rows [::categories-table-rows]
                          :default-sort-column :display-order}]
    [:div.mt-3
     [table/search categories-table]
     [table/table categories-table]]))

(defn categories-page []
  (into [:div
         [administration/navigator]
         [document-title (text :t.administration/categories)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[roles/show-when roles/+admin-write-roles+
            [to-create-category]]
           [categories-list]])))
