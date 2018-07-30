(ns rems.config
  (:require [re-frame.core :as rf]
            [rems.util :refer [fetch]]))

(rf/reg-event-db
  ::loaded-config
  (fn [db [_ config]]
    (assoc db :config config
              :language (:default-language config :en))))

(rf/reg-sub
  ::config
  (fn [db _]
    (:config db)))

(defn fetch-config! []
  (fetch "/api/config" {:handler #(rf/dispatch [:rems.config/loaded-config %])}))
