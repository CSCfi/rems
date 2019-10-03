(ns rems.config
  (:require [re-frame.core :as rf]
            [rems.flash-message :as flash-message]
            [rems.util :refer [fetch]]))

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
