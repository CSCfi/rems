(ns rems.config
  (:require [re-frame.core :as rf]
            [rems.common.util :refer [index-by]]
            [rems.flash-message :as flash-message]
            [rems.util :refer [fetch]]))

;; config

(rf/reg-event-db
 ::loaded-config
 (fn [db [_ config]]
   (assoc db
          :config config
          :default-language (get config :default-language)
          :languages (get config :languages))))

(rf/reg-sub
 ::config
 (fn [db _]
   (:config db)))

(defn fetch-config! []
  (fetch "/api/config"
         {:handler #(rf/dispatch-sync [::loaded-config %])
          :error-handler (flash-message/default-error-handler :top "Fetch config")}))

(defn dev-environment? []
  (let [config @(rf/subscribe [::config])]
    (boolean (:dev config))))

;; organizations

(rf/reg-sub
 :organization-by-id
 (fn [db _]
   (:organization-by-id db {})))

(rf/reg-sub
 :organizations
 (fn [_db _]
   [(rf/subscribe [:organization-by-id])
    (rf/subscribe [:language])])
 (fn [[organization-by-id language]]
   (sort-by (comp language :organization/name) (vals organization-by-id))))

(rf/reg-sub
 :owned-organizations
 (fn [db _]
   (let [roles (get-in db [:identity :roles])
         userid (get-in db [:identity :user :userid])]
     (for [org (vals (:organization-by-id db))
           :let [owners (set (map :userid (:organization/owners org)))]
           :when (or (contains? roles :owner)
                     (contains? owners userid))]
       org))))

(rf/reg-event-db
 :loaded-organizations
 (fn [db [_ organizations]]
   (assoc db :organization-by-id (index-by [:organization/id] organizations))))

(defn fetch-organizations! []
  (fetch "/api/organizations"
         {:params {:disabled true :archived true}
          :handler #(rf/dispatch-sync [:loaded-organizations %])
          :error-handler (flash-message/default-error-handler :top "Fetch organizations")}))
