(ns rems.administration.catalogue-items
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.catalogue-item :as catalogue-item]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text get-localized-title]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch-n [[::fetch-catalogue]
                 [:rems.table/reset]]}))

(rf/reg-event-fx
 ::fetch-catalogue
 (fn [{:keys [db]}]
   (let [description (text :t.administration/catalogue-items)]
     (fetch "/api/catalogue-items"
            {:url-params {:expand :names
                          :disabled true
                          :expired (::display-old? db)
                          :archived (::display-old? db)}
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

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-catalogue]}))
(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))

(defn- to-create-catalogue-item []
  [atoms/link {:class "btn btn-primary"}
   "/#/administration/create-catalogue-item"
   (text :t.administration/create-catalogue-item)])

(defn- to-catalogue-item [catalogue-item-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/#/administration/catalogue-items/" catalogue-item-id)
   (text :t.administration/view)])

(rf/reg-sub
 ::catalogue-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue])
    (rf/subscribe [:language])])
 (fn [[catalogue language] _]
   (map (fn [item]
          {:key (:id item)
           :name {:value (get-localized-title item language)}
           :resource (let [value (:resource-name item)]
                       {:value value
                        :td [:td.resource
                             [atoms/link nil
                              (str "#/administration/resources/" (:resource-id item))
                              value]]})
           :form (let [value (:form-name item)]
                   {:value value
                    :td [:td.form
                         [atoms/link nil
                          (str "#/administration/forms/" (:formid item))
                          value]]})
           :workflow (let [value (:workflow-name item)]
                       {:value value
                        :td [:td.workflow
                             [atoms/link nil
                              (str "#/administration/workflows/" (:wfid item))
                              value]]})
           :created (let [value (:start item)]
                      {:value value
                       :display-value (localize-time value)})
           :active (let [checked? (status-flags/active? item)]
                     {:td [:td.active
                           [readonly-checkbox checked?]]
                      :sort-value (if checked? 1 2)})
           :commands {:td [:td.commands
                           [to-catalogue-item (:id item)]
                           [catalogue-item/edit-button (:id item)]
                           [status-flags/enabled-toggle item #(rf/dispatch [::set-catalogue-item-enabled %1 %2 [::fetch-catalogue]])]
                           [status-flags/archived-toggle item #(rf/dispatch [::set-catalogue-item-archived %1 %2 [::fetch-catalogue]])]]}})
        catalogue)))

(defn- catalogue-list []
  (let [catalogue-table {:id ::catalogue
                         :columns [{:key :name
                                    :title (text :t.catalogue/header)}
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
                                    :filterable? false}]
                         :rows [::catalogue-table-rows]
                         :default-sort-column :name}]
    [:div.mt-3
     [table/search catalogue-table]
     [table/table catalogue-table]]))

(defn catalogue-items-page []
  (into [:div
         [administration-navigator-container]
         [document-title (text :t.administration/catalogue-items)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-catalogue-item]
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [catalogue-list]])))
