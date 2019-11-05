(ns rems.administration.blacklist
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.application-util]
            [rems.atoms :as atoms]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch {}]
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
   (for [{resource :blacklist/resource user :blacklist/user} rows]
     {:key (str "blacklist-" resource (:userid user))
      :resource {:value (:resource/ext-id resource)}
      :user {:value (rems.application-util/get-member-name user)}
      :email {:value (:email user)}})))

(rf/reg-event-db
 ::fetch-result
 (fn [db [_ rows]]
   (-> db
       (assoc ::blacklist (format-rows rows))
       (dissoc ::loading?))))

(rf/reg-sub ::blacklist (fn [db _] (::blacklist db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- blacklist [rows]
  (let [table-spec {:id ::blacklist
                    :columns [{:key :resource
                               :title (text :t.administration/resource)}
                              {:key :user
                               :title (text :t.administration/user)}
                              {:key :email
                               :title (text :t.applicant-info/email)}]
                    :rows [::blacklist]
                    :default-sort-column :resource}]
    [:div.mt-3
     [table/search table-spec]
     [table/table table-spec]]))

(defn blacklist-page []
  [:div
   [administration-navigator-container]
   [atoms/document-title (text :t.administration/blacklist)]
   [flash-message/component :top]
   (if @(rf/subscribe [::loading?])
     [spinner/big]
     [blacklist @(rf/subscribe [::blacklist])])])
