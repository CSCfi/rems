(ns rems.config
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.common.util :refer [index-by]]
            [rems.globals]
            [rems.util :refer [fetch]]))

;; config

(def current-language
  "Current user language, or default language from config."
  (r/reaction
   (or @rems.globals/language
       (:default-language @rems.globals/config))))

(def dev-environment?
  "True when using development mode configuration."
  (r/reaction
   (true? (:dev @rems.globals/config))))

(def languages
  "List of available languages from configuration. Sorted by default language first."
  (r/reaction
   (let [default-lang (:default-language @rems.globals/config)
         languages (:languages @rems.globals/config)]
     (into [default-lang]
           (sort (remove #{default-lang} languages))))))

;; organizations

(rf/reg-sub
 :organization-by-id
 (fn [db _]
   (:organization-by-id db {})))

(rf/reg-sub
 :organizations
 :<- [:organization-by-id]
 (fn [organization-by-id]
   (sort-by (comp @rems.config/current-language :organization/name) (vals organization-by-id))))

(rf/reg-sub
 :owned-organizations
 (fn [db _]
   (let [roles @rems.globals/roles
         userid (:userid @rems.globals/user)]
     (doall
      (for [org (vals (:organization-by-id db))
            :let [owners (set (map :userid (:organization/owners org)))]
            :when (or (contains? roles :owner)
                      (contains? owners userid))]
        org)))))

(rf/reg-event-db
 :loaded-organizations
 (fn [db [_ organizations]]
   (assoc db :organization-by-id (index-by [:organization/id] organizations))))

(defn fetch-organizations! [{on-error :error-handler}]
  (fetch "/api/organizations"
         {:params {:disabled true :archived true}
          :handler #(rf/dispatch-sync [:loaded-organizations %])
          :error-handler on-error}))

(rf/reg-sub
 :handled-organizations
 (fn [db _]
   (:handled-organizations db)))

(rf/reg-event-db
 :loaded-handled-organizations
 (fn [db [_ organizations]]
   (assoc db :handled-organizations organizations)))

(defn fetch-handled-organizations! [{on-error :error-handler}]
  (fetch "/api/organizations/handled"
         {:handler #(rf/dispatch-sync [:loaded-handled-organizations %])
          :error-handler on-error}))
