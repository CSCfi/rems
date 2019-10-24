(ns rems.administration.licenses
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text get-localized-title]]
            [rems.util :refer [put! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch-licenses]
                 [:rems.table/reset]]}))

(rf/reg-event-db
 ::fetch-licenses
 (fn [db]
   (let [description [text :t.administration/licenses]]
     (fetch "/api/licenses"
            {:url-params {:disabled true
                          :archived (status-flags/display-archived? db)}
             :handler #(rf/dispatch [::fetch-licenses-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   (assoc db ::loading? true)))

(rf/reg-event-db
 ::fetch-licenses-result
 (fn [db [_ licenses]]
   (-> db
       (assoc ::licenses licenses)
       (dissoc ::loading?))))

(rf/reg-sub ::licenses (fn [db _] (::licenses db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::set-license-archived
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/licenses/archived"
         {:params (select-keys item [:id :archived])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(rf/reg-event-fx
 ::set-license-enabled
 (fn [_ [_ item description dispatch-on-finished]]
   (put! "/api/licenses/enabled"
         {:params (select-keys item [:id :enabled])
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(defn- to-create-license []
  [atoms/link {:class "btn btn-primary"}
   "/administration/licenses/create"
   (text :t.administration/create-license)])

(defn- to-view-license [license-id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/licenses/" license-id)
   (text :t.administration/view)])

(rf/reg-sub
 ::licenses-table-rows
 (fn [_ _]
   [(rf/subscribe [::licenses])
    (rf/subscribe [:language])])
 (fn [[licenses language] _]
   (map (fn [license]
          {:key (:id license)
           :title {:value (get-localized-title license language)}
           :type {:value (:licensetype license)}
           :active (let [checked? (status-flags/active? license)]
                     {:td [:td.active
                           [readonly-checkbox checked?]]
                      :sort-value (if checked? 1 2)})
           :commands {:td [:td.commands
                           [to-view-license (:id license)]
                           [status-flags/enabled-toggle license #(rf/dispatch [::set-license-enabled %1 %2 [::fetch-licenses]])]
                           [status-flags/archived-toggle license #(rf/dispatch [::set-license-archived %1 %2 [::fetch-licenses]])]]}})
        licenses)))

(defn- licenses-list []
  (let [licenses-table {:id ::licenses
                        :columns [{:key :title
                                   :title (text :t.administration/licenses)}
                                  {:key :type
                                   :title (text :t.administration/type)}
                                  {:key :active
                                   :title (text :t.administration/active)
                                   :filterable? false}
                                  {:key :commands
                                   :sortable? false
                                   :filterable? false}]
                        :rows [::licenses-table-rows]
                        :default-sort-column :title}]
    [:div.mt-3
     [table/search licenses-table]
     [table/table licenses-table]]))

(defn licenses-page []
  (into [:div
         [administration-navigator-container]
         [document-title (text :t.administration/licenses)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-license]
           [status-flags/display-archived-toggle #(rf/dispatch [::fetch-licenses])]
           [status-flags/disabled-and-archived-explanation]
           [licenses-list]])))
