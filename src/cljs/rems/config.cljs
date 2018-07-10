(ns rems.config
  (:require [ajax.core :refer [GET]]
            [re-frame.core :as rf]))

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
  (GET "/api/config" {:handler #(rf/dispatch [:rems.config/loaded-config %])
                      :response-format :transit
                      :keywords? true}))
