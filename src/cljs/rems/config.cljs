(ns rems.config
  (:require [re-frame.core :as rf]
            [rems.util :refer [fetch]]))

(rf/reg-event-db
  ::loaded-config
  (fn [db [_ config]]
    (let [default-language (:default-language config (:default-language db))]
      (assoc db :config config
                :default-language default-language
                :language default-language))))

(rf/reg-sub
  ::config
  (fn [db _]
    (:config db)))

(defn fetch-config! []
  (fetch "/api/config" {:handler #(rf/dispatch [::loaded-config %])}))
