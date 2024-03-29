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

(defn ^:export fetch-config! [& [callback]]
  (fetch "/api/config"
         {:handler #(do (rf/dispatch-sync [::loaded-config %])
                        (when callback (callback %)))
          :error-handler (flash-message/default-error-handler :top "Fetch config")}))

(defn dev-environment? []
  (let [config @(rf/subscribe [::config])]
    (boolean (:dev config))))

(defn ^:export set-config! [js-config]
  (when (dev-environment?)
    (rf/dispatch-sync [::loaded-config (merge @(rf/subscribe [::config])
                                              (js->clj js-config :keywordize-keys true))])))

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

(rf/reg-sub
 :handled-organizations
 (fn [db _]
   (:handled-organizations db)))

(rf/reg-event-db
 :loaded-handled-organizations
 (fn [db [_ organizations]]
   (assoc db :handled-organizations organizations)))

(defn fetch-handled-organizations! []
  (fetch "/api/organizations/handled"
         {:handler #(rf/dispatch-sync [:loaded-handled-organizations %])
          :error-handler (flash-message/default-error-handler :top "Fetch handled organizations")}))
