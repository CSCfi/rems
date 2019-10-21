(ns rems.administration.blacklist
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.application-util]
            [rems.atoms :as atoms]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch {}]
                 [::fetch-resources]
                 [:rems.table/reset]]}))

(rf/reg-event-fx
 ::fetch
 (fn [{:keys [db]} [_ params]]
   (let [description [text :t.administration/blacklist]]
     (fetch "/api/blacklist"
            {:url-params params
             :handler #(rf/dispatch [::fetch-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   {:db (assoc db ::loading? true)}))

(defn- format-rows [rows]
  (doall
   (for [{:keys [resource user]} rows]
     {:key (str "blacklist-" resource (:userid user))
      :resource {:value resource}
      :user {:value (rems.application-util/get-member-name user)}})))

(rf/reg-event-db
 ::fetch-result
 (fn [db [_ rows]]
   (-> db
       (assoc ::blacklist (format-rows rows))
       (dissoc ::loading?))))

(rf/reg-sub ::blacklist (fn [db _] (::blacklist db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::fetch-resources
 (fn [_ _]
   (fetch "/api/resources"
          {:url-params {:disabled true
                        :archived true}
           :handler #(rf/dispatch [::fetch-resources-result %])
           :error-handler (flash-message/default-error-handler :top "Fetch resources")})
   {}))

(rf/reg-event-db
 ::fetch-resources-result
 (fn [db [_ resources]]
   (assoc db ::resources resources)))

(rf/reg-sub ::resources (fn [db _] (::resources db)))

(rf/reg-event-fx
 ::set-resource-filter
 (fn [{:keys [db]} [_ id]]
   {:db (assoc db ::resource-filter id)
    :dispatch [::fetch {:resource id}]}))
(rf/reg-sub ::resource-filter (fn [db _] (::resource-filter db)))

(defn- blacklist [rows]
  (let [table-spec {:id ::blacklist
                    :columns [{:key :resource
                               :title "resource"}
                              {:key :user
                               :title "user"}]
                    :rows [::blacklist]
                    :default-sort-column :resource}]
    [:div.mt-3
     [table/search table-spec]
     [table/table table-spec]]))

(defn- filter-resource-field []
  (let [resources @(rf/subscribe [::resources])
        selected-resource-id @(rf/subscribe [::resource-filter])
        item-selected? #(= (:resid %) selected-resource-id)
        id "blacklist-filter-resource"]
    [:div.form-group
     [:label {:for id} (text :t.create-catalogue-item/resource-selection)] ;; TODO
     [dropdown/dropdown
      {:id id
       :items resources
       :item-key :resid
       :item-label :resid
       :item-selected? item-selected?
       :on-change #(rf/dispatch [::set-resource-filter (:resid %)])}]]))

(defn blacklist-page []
  [:div
   [administration-navigator-container]
   [atoms/document-title (text :t.administration/blacklist)]
   [flash-message/component :top]
   (if @(rf/subscribe [::loading?])
     [spinner/big]
     [:<>
      [filter-resource-field]
      [blacklist @(rf/subscribe [::blacklist])]])])
