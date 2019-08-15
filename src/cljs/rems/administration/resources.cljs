(ns rems.administration.resources
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch-n [[::fetch-resources]
                 [:rems.table/reset]]}))

(rf/reg-event-fx
 ::fetch-resources
 (fn [{:keys [db]}]
   (fetch "/api/resources" {:url-params {:disabled true
                                         :expired (::display-old? db)
                                         :archived (::display-old? db)}
                            :handler #(rf/dispatch [::fetch-resources-result %])
                            :error-handler status-modal/common-error-handler!})
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-resources-result
 (fn [db [_ resources]]
   (-> db
       (assoc ::resources resources)
       (dissoc ::loading?))))

(rf/reg-sub ::resources (fn [db _] (::resources db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::update-resource
 (fn [{:keys [db]} [_ item description]]
   (status-modal/common-pending-handler! description)
   (put! "/api/resources/update"
         {:params (select-keys item [:id :enabled :archived])
          :handler (partial status-flags/common-update-handler! #(rf/dispatch [::fetch-resources]))
          :error-handler status-modal/common-error-handler!})
   {}))

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-resources]}))
(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))

(defn- to-create-resource []
  [atoms/link {:class "btn btn-primary"}
   "/#/administration/create-resource"
   (text :t.administration/create-resource)])

(defn- to-view-resource [resource-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/#/administration/resources/" resource-id)
   (text :t.administration/view)])

(rf/reg-sub
 ::resources-table-rows
 (fn [_ _]
   [(rf/subscribe [::resources])])
 (fn [[resources] _]
   (map (fn [resource]
          {:key (:id resource)
           :organization {:value (:organization resource)}
           :title {:value (:resid resource)}
           :start (let [value (:start resource)]
                    {:value value
                     :display-value (localize-time value)})
           :end (let [value (:end resource)]
                  {:value value
                   :display-value (localize-time value)})
           :active (let [checked? (not (:expired resource))]
                     {:td [:td.active
                           [readonly-checkbox checked?]]
                      :sort-value (if checked? 1 2)})
           :commands {:td [:td.commands
                           [to-view-resource (:id resource)]
                           [status-flags/enabled-toggle resource #(rf/dispatch [::update-resource %1 %2])]
                           [status-flags/archived-toggle resource #(rf/dispatch [::update-resource %1 %2])]]}})
        resources)))

(defn- resources-list []
  (let [resources-table {:id ::resources
                         :columns [{:key :organization
                                    :title (text :t.administration/organization)}
                                   {:key :title
                                    :title (text :t.administration/resource)}
                                   {:key :start
                                    :title (text :t.administration/created)}
                                   {:key :end
                                    :title (text :t.administration/end)}
                                   {:key :active
                                    :title (text :t.administration/active)
                                    :filterable? false}
                                   {:key :commands
                                    :sortable? false
                                    :filterable? false}]
                         :rows [::resources-table-rows]
                         :default-sort-column :title}]
    [:div.mt-3
     [table/search resources-table]
     [table/table resources-table]]))

(defn resources-page []
  (into [:div
         [administration-navigator-container]
         [document-title (text :t.administration/resources)]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-resource]
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [resources-list]])))
