(ns rems.administration.catalogue-items
  (:require [cljs-time.coerce :as time-coerce]
            [reagent.core :as r]
            [medley.core :refer [index-by]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.catalogue-item :as catalogue-item]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.common.util :refer [select-vals]]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text get-localized-title localized]]
            [rems.util :refer [navigate! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db db
    :dispatch-n [[::fetch-catalogue]
                 [:rems.table/reset]
                 [:rems.administration.administration/remember-current-page]]}))

(rf/reg-event-fx
 ::fetch-catalogue
 (fn [{:keys [db]}]
   (let [description [text :t.administration/catalogue-items]]
     (fetch "/api/catalogue-items"
            {:url-params {:expand :names
                          :disabled true
                          :expired (status-flags/display-archived? db)
                          :archived (status-flags/display-archived? db)}
             :handler #(rf/dispatch [::fetch-catalogue-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading?))))

(rf/reg-sub ::catalogue (fn [db _] (::catalogue db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::set-catalogue-item-archived
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/catalogue-items/archived"
         {:params (select-keys item [:id :archived])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(rf/reg-event-fx
 ::set-catalogue-item-enabled
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/catalogue-items/enabled"
         {:params (select-keys item [:id :enabled])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(rf/reg-sub
 ::catalogue-by-ids
 :<- [::catalogue]
 (fn [items]
   (index-by :id items)))

(rf/reg-sub
 ::selected-catalogue-items
 :<- [::catalogue-by-ids]
 :<- [:rems.table/selected-rows {:id ::catalogue}]
 (fn [[catalogue-by-ids selected-item-ids] _]
   (select-vals catalogue-by-ids selected-item-ids)))

(defn- create-catalogue-item-button []
  [atoms/link {:class "btn btn-primary" :id :create-catalogue-item}
   "/administration/catalogue-items/create"
   (text :t.administration/create-catalogue-item)])

(defn- update-catalogue-item-button [items]
  [atoms/rate-limited-action-button
   {:id :update-catalogue-item
    :class "btn-primary"
    :disabled (when (empty? items) :disabled)
    :on-click (fn []
                (rf/dispatch [:rems.administration.update-catalogue-item/enter-page items])
                (navigate! "/administration/catalogue-items/update-catalogue-item"))
    :label [text :t.administration/update-catalogue-item]}])

(defn- categories-button []
  [atoms/link {:class "btn btn-primary" :id :manage-categories}
   "/administration/categories"
   (text :t.administration/manage-categories)])

(defn- view-button [catalogue-item-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/catalogue-items/" catalogue-item-id)
   (text :t.administration/view)])

(defn- modify-item-dropdown [item]
  [atoms/commands-group-button
   {:label (text :t.actions/modify)}
   (when (roles/can-modify-organization-item? item)
     (list (catalogue-item/edit-action (:id item))
           (status-flags/enabled-toggle-action {:on-change #(rf/dispatch [::set-catalogue-item-enabled %1 %2 [::fetch-catalogue]])} item)
           (status-flags/archived-toggle-action {:on-change #(rf/dispatch [::set-catalogue-item-archived %1 %2 [::fetch-catalogue]])} item)))])

(rf/reg-sub
 ::catalogue-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue])
    (rf/subscribe [:rems.administration.administration/displayed-organization-ids])])
 (fn [[catalogue displayed-organization-ids] _]
   (->> catalogue
        (administration/filter-by-displayed-organization displayed-organization-ids #(get-in % [:organization :organization/id]))
        (mapv (fn [item]
                {:key (:id item)
                 :organization {:value (localized (get-in item [:organization :organization/short-name]))}
                 :name {:value (get-localized-title item)
                        :sort-value [(get-localized-title item)
                                     (- (time-coerce/to-long (:start item)))]} ; secondary sort by created, reverse
                 :resource (let [value (:resource-name item)]
                             {:value value
                              :display-value [atoms/link nil
                                              (str "/administration/resources/" (:resource-id item))
                                              value]})
                 :form (if-let [value (:form-name item)]
                         {:value value
                          :display-value [atoms/link nil
                                          (str "/administration/forms/" (:formid item))
                                          value]}
                         {:value (text :t.administration/no-form)
                          :sort-value "" ; push "No form" cases to the top of the list when sorting
                          :display-value [text :t.administration/no-form]})
                 :workflow (let [value (:workflow-name item)]
                             {:value value
                              :display-value [atoms/link nil
                                              (str "/administration/workflows/" (:wfid item))
                                              value]})
                 :created (let [value (:start item)]
                            {:value value
                             :display-value (localize-time value)})
                 :active (let [checked? (status-flags/active? item)]
                           {:display-value [readonly-checkbox {:value checked?}]
                            :sort-value (if checked? 1 2)})
                 :commands {:display-value [:div.commands
                                            [view-button (:id item)]
                                            [modify-item-dropdown item]]}})))))

(defn- catalogue-list []
  [table/standard {:id ::catalogue
                   :columns [{:key :organization
                              :title (text :t.administration/organization)}
                             {:key :name
                              :title (text :t.administration/title)}
                             {:key :resource
                              :title (text :t.administration/resource)}
                             {:key :form
                              :title (text :t.administration/form)}
                             {:key :workflow
                              :title (text :t.administration/workflow)}
                             {:key :created
                              :title (text :t.administration/created)}
                             {:key :active
                              :title (text :t.administration/active)
                              :filterable? false}
                             {:key :commands
                              :sortable? false
                              :filterable? false
                              :aria-label (text :t.actions/commands)}]
                   :rows [::catalogue-table-rows]
                   :default-sort-column :created
                   :default-sort-order :desc
                   :selectable? true}])

(defn catalogue-items-page []
  (into [:div
         [administration/navigator]
         [document-title (text :t.administration/catalogue-items)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[roles/show-when roles/+admin-write-roles+
            [atoms/commands
             [create-catalogue-item-button]
             [categories-button]
             [update-catalogue-item-button @(rf/subscribe [::selected-catalogue-items])]]
            [status-flags/status-flags-intro #(do (rf/dispatch [::fetch-catalogue])
                                                  (rf/dispatch [:rems.table/set-selected-rows {:id ::catalogue} nil]))]]
           [administration/own-organization-selection]
           [catalogue-list]])))
