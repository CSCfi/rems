(ns rems.config
  (:require [re-frame.core :as rf]
            [rems.util :refer [fetch]]))

(rf/reg-event-db
  ::loaded-config
  (fn [db [_ config]]
    (let [hardcoded-default-language (get db :default-language)
          configured-default-language (get config :default-language hardcoded-default-language)]
      (assoc db :config config
                :default-language configured-default-language
                :language configured-default-language))))

(rf/reg-sub
  ::config
  (fn [db _]
    (:config db)))

(defn fetch-config! []
  (fetch "/api/config" {:handler #(rf/dispatch [::loaded-config %])}))

(defn dev-environment? []
  (let [config @(rf/subscribe [::config])]
    (boolean (:dev config))))
