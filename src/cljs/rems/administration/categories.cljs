(ns rems.administration.categories
  (:require [cljs-time.coerce :as time-coerce]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.catalogue-item :as catalogue-item]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text get-localized-title]]
            [rems.util :refer [navigate! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::selected-items (or (::selected-items db) #{}))
    :dispatch-n [[::fetch-categories]
                ;;  [:rems.table/reset]
                 [:rems.administration.administration/remember-current-page]]}))

(rf/reg-event-fx
 ::fetch-categories
 (fn [{:keys [db]}]
   (let [description [text :t.administration/categories]]
     (fetch "/api/categories"
            {:handler #(rf/dispatch [::fetch-categories-result %])
             :error-handler #(flash-message/default-error-handler :top description)}))
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-categories-result
 (fn [db [_ categories]]
   (-> db
       (assoc ::categories categories)
       (dissoc ::loading?))))

(rf/reg-sub ::categories (fn [db _] (::categories db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

;; (rf/reg-event-db
;;  ::set-selected-items
;;  (fn [db [_ items]]
;;    (assoc db ::selected-items items)))

;; (rf/reg-sub
;;  ::selected-items
;;  (fn [db _]
;;    (::selected-items db)))


(defn- to-view-category [category-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/categories/" category-id)
   (text :t.administration/view)])

(defn edit-button [id]
  [atoms/link {:class "btn btn-primary edit-workflow"}
   (str "/administration/categories/edit/" id)
   (text :t.administration/edit)])

(defn- to-create-category []
  [atoms/link {:id "create-category"
               :class "btn btn-primary"}
   "/administration/categories/create"
   (text :t.administration/create-category)])

(rf/reg-sub
 ::categories-table-rows
 (fn [_ _]
   [(rf/subscribe [::categories])
    (rf/subscribe [:language])])
 (fn [[categories language] _]
   (map (fn [category]
          {:key (:id category)
           :organization {:value (get-in category [:organization :organization/id])}
           :id {:value (:id category)}
           :name {:value (language (:title (js->clj (. js/JSON (parse (get-in category [:data]))) :keywordize-keys true)))}
           :commands {:td [:td.commands
                           [to-view-category (:id category)]
                           [edit-button (:id category)]]}})
        categories)))

(defn- categories-list []
  (let [categories-table {:id ::categories
                          :columns [{:key :id
                                     :title (text :t.administration/category-id)}
                                    {:key :name
                                     :title (text :t.administration/category-name)}
                                    {:key :organization
                                     :title (text :t.administration/organization)}
                                    {:key :commands
                                     :sortable? false
                                     :filterable? false}]
                          :rows [::categories-table-rows]
                          :default-sort-column :title}]
    [:div.mt-3
     [table/search categories-table]
     [table/table categories-table]]))

(defn categories-page []
  (let [loading? (rf/subscribe [::loading?])
        categories (rf/subscribe [::categories])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/categories)]
     [flash-message/component :top]
     (if @loading?
       [spinner/big]
       [:div
        [to-create-category]
        [categories-list]
        ;; [roles/show-when roles/+admin-write-roles+
        ;;  [:div.commands.text-left.pl-0
        ;;   [:div "buttons go here"]
        ;;     ;;  [create-category-button]
        ;;     ;;  [change-form-button (items-by-ids @(rf/subscribe [::catalogue]) @(rf/subscribe [::selected-items]))]
        ;;   ]
        ;;     ;; [status-flags/display-archived-toggle #(do (rf/dispatch [::fetch-catalogue])
        ;;     ;;                                            (rf/dispatch [:rems.table/set-selected-rows {:id ::catalogue} nil]))]
        ;;     ;; [status-flags/disabled-and-archived-explanation]
        ;;  ]
        ;;  [catalogue-list]
        ])]))
