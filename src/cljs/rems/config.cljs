(ns rems.config
  (:require [re-frame.core :as rf]))

(rf/reg-event-db
  ::loaded-config
  (fn [db [_ config]]
    (assoc db :config config)))

(rf/reg-sub
  ::config
  (fn [db _]
    (:config db)))
