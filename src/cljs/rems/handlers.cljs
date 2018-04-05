(ns rems.handlers
  (:require [rems.db :as db]
            [re-frame.core :refer [dispatch reg-event-db]]))

(reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  :set-active-page
  (fn [db [_ page]]
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
  :loaded-theme
  (fn [db [_ theme]]
    (assoc db :theme theme)))

(reg-event-db
 :set-identity
 (fn [db [_ identity]]
   (assoc db :identity identity)))

(reg-event-db
 :set-current-language
 (fn [db [_ language]]
   (assoc db :language language)))
