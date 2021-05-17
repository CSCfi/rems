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
            {;;  :url-params {:expand :names
            ;;               :disabled true
            ;;               :expired (status-flags/display-archived? db)
            ;;               :archived (status-flags/display-archived? db)}
             :handler #(rf/dispatch [::fetch-categories-result %])
             :error-handler #(js/console.log (clj->js %))
            ;;  (flash-message/default-error-handler :top description)
             }))
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-categories-result
 (fn [db [_ categories]]
   (-> db
       (assoc ::categories categories)
       (dissoc ::loading?))))

(rf/reg-sub ::categories (fn [db _] (::categories db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

;; (rf/reg-event-fx
;;  ::set-catalogue-item-archived
;;  (fn [_ [_ item description dispatch-on-finished]]
;;    (put! "/api/catalogue-items/archived"
;;          {:params (select-keys item [:id :archived])
;;           :handler (flash-message/status-update-handler
;;                     :top description #(rf/dispatch dispatch-on-finished))
;;           :error-handler (flash-message/default-error-handler :top description)})
;;    {}))

;; (rf/reg-event-fx
;;  ::set-catalogue-item-enabled
;;  (fn [_ [_ item description dispatch-on-finished]]
;;    (put! "/api/catalogue-items/enabled"
;;          {:params (select-keys item [:id :enabled])
;;           :handler (flash-message/status-update-handler
;;                     :top description #(rf/dispatch dispatch-on-finished))
;;           :error-handler (flash-message/default-error-handler :top description)})
;;    {}))

;; (rf/reg-event-db
;;  ::set-selected-items
;;  (fn [db [_ items]]
;;    (assoc db ::selected-items items)))

;; (rf/reg-sub
;;  ::selected-items
;;  (fn [db _]
;;    (::selected-items db)))

;; (defn- create-category-button []
;;   [atoms/link {:class "btn btn-primary" :id :create-catalogue-item}
;;    "/administration/categories/create"
;;    (text :t.administration/create-catalogue-item)])

;; (defn- change-form-button [items]
;;   [:button.btn.btn-primary
;;    {:disabled (when (empty? items) :disabled)
;;     :on-click (fn []
;;                 (rf/dispatch [:rems.administration.change-catalogue-item-form/enter-page items])
;;                 (navigate! "/administration/catalogue-items/change-form"))}
;;    (text :t.administration/change-form)])

;; (defn- view-button [catalogue-item-id]
;;   [atoms/link {:class "btn btn-primary"}
;;    (str "/administration/catalogue-items/" catalogue-item-id)
;;    (text :t.administration/view)])

;; (rf/reg-sub
;;  ::catalogue-table-rows
;;  (fn [_ _]
;;    [(rf/subscribe [::catalogue])
;;     (rf/subscribe [:language])])
;;  (fn [[catalogue language] _]
;;    (map (fn [item]
;;           {:key (:id item)
;;            :organization {:value (get-in item [:organization :organization/short-name language])}
;;            :name {:value (get-localized-title item language)
;;                   :sort-value [(get-localized-title item language)
;;                                (- (time-coerce/to-long (:start item)))]} ; secondary sort by created, reverse
;;            :resource (let [value (:resource-name item)]
;;                        {:value value
;;                         :td [:td.resource
;;                              [atoms/link nil
;;                               (str "/administration/resources/" (:resource-id item))
;;                               value]]})
;;            :form (if-let [value (:form-name item)]
;;                    {:value value
;;                     :td [:td.form
;;                          [atoms/link nil
;;                           (str "/administration/forms/" (:formid item))
;;                           value]]}
;;                    {:value (text :t.administration/no-form)
;;                     :sort-value "" ; push "No form" cases to the top of the list when sorting
;;                     :td [:td.form [text :t.administration/no-form]]})
;;            :workflow (let [value (:workflow-name item)]
;;                        {:value value
;;                         :td [:td.workflow
;;                              [atoms/link nil
;;                               (str "/administration/workflows/" (:wfid item))
;;                               value]]})
;;            :created (let [value (:start item)]
;;                       {:value value
;;                        :display-value (localize-time value)})
;;            :active (let [checked? (status-flags/active? item)]
;;                      {:td [:td.active
;;                            [readonly-checkbox {:value checked?}]]
;;                       :sort-value (if checked? 1 2)})
;;            :commands {:td [:td.commands
;;                            [view-button (:id item)]
;;                            [roles/show-when roles/+admin-write-roles+
;;                             [catalogue-item/edit-button (:id item)]
;;                             [status-flags/enabled-toggle item #(rf/dispatch [::set-catalogue-item-enabled %1 %2 [::fetch-catalogue]])]
;;                             [status-flags/archived-toggle item #(rf/dispatch [::set-catalogue-item-archived %1 %2 [::fetch-catalogue]])]]]}})
;;         catalogue)))

;; (defn- catalogue-list []
;;   (let [catalogue-table {:id ::catalogue
;;                          :columns [{:key :organization
;;                                     :title (text :t.administration/organization)}
;;                                    {:key :name
;;                                     :title (text :t.catalogue/header)}
;;                                    {:key :resource
;;                                     :title (text :t.administration/resource)}
;;                                    {:key :form
;;                                     :title (text :t.administration/form)}
;;                                    {:key :workflow
;;                                     :title (text :t.administration/workflow)}
;;                                    {:key :created
;;                                     :title (text :t.administration/created)}
;;                                    {:key :active
;;                                     :title (text :t.administration/active)
;;                                     :filterable? false}
;;                                    {:key :commands
;;                                     :sortable? false
;;                                     :filterable? false}]
;;                          :rows [::catalogue-table-rows]
;;                          :default-sort-column :name
;;                          :selectable? true
;;                          :on-select #(rf/dispatch [::set-selected-items %])}]
;;     [:div.mt-3
;;      [table/search catalogue-table]
;;      [table/table catalogue-table]]))

;; (defn- items-by-ids [items ids]
;;   (filter (comp ids :id) items))

(rf/reg-sub
 ::categories-table-rows
 (fn [_ _]
   [(rf/subscribe [::categories])
    (rf/subscribe [:language])])
 (fn [[categories language] _]
   (map (fn [category]
          {:key (:id category)
          ;;  :organization {:value (get-in resource [:organization :organization/short-name language])}
           :id {:value (:id category)}
          ;;  :active (let [checked? (status-flags/active? resource)]
          ;;            {:td [:td.active
          ;;                  [readonly-checkbox {:value checked?}]]
          ;;             :sort-value (if checked? 1 2)}
          ;;            )
          ;;  :commands {:td
          ;;             [:td.commands (:id category)]
          ;;                 ;;  [to-view-resource (:id resource)]
          ;;                 ;;  [roles/show-when roles/+admin-write-roles+
          ;;                 ;;   [status-flags/enabled-toggle resource #(rf/dispatch [::set-resource-enabled %1 %2 [::fetch-resources]])]
          ;;                 ;;   [status-flags/archived-toggle resource #(rf/dispatch [::set-resource-archived %1 %2 [::fetch-resources]])]]]
          ;;             }
           })
        categories)))

(defn- categories-list []
  (let [resources-table {:id ::categories
                         :columns [{:key :id
                                    :title (text :t.administration/organization)}
                                  ;;  {:key :commands
                                  ;;   :sortable? false
                                  ;;   :filterable? false}
                                   ]
                         :rows [::categories-table-rows]
                         :default-sort-column :title}]
    [:div.mt-3
     [table/search resources-table]
     [table/table resources-table]]))

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
        (map
         (fn [c]
           [:div
            [:p (:id c)]
            [:p (str (:data c))]
            [:p (str (:organization c))]])
         @categories)
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
        [:div "here should be categories list"]])]))
