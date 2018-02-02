(ns rems.handlers
  (:require [rems.db :as db]
            [rems.application :refer [fetch-application]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx]]))

(reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  :set-active-page
  (fn [db [_ page]]
    (println :set-active-page page)
    (assoc db :page page)))

(reg-event-db
  :set-docs
  (fn [db [_ docs]]
    (assoc db :docs docs)))

(reg-event-db
  :loaded-translations
  (fn [db [_ translations]]
    (assoc db :translations translations)))

(reg-event-db
 :set-user
 (fn [db [_ user]]
   (assoc db :user user)))

(reg-event-db
 :catalogue
 (fn [db [_ catalogue]]
   (assoc db :catalogue catalogue)))

(reg-event-db
 :application
 (fn [db [_ application]]
   (assoc db :application application)))

(reg-event-fx
 :fetch-application
 (fn [coeff [_ id]]
   {:fetch-application [(get-in coeff [:db :user]) id]}))

(reg-fx
 :fetch-application
 (fn [[user id]]
   (fetch-application user id)))
