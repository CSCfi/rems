(ns rems.subscriptions
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub
  :page
  (fn [db _]
    (:page db)))

(reg-sub
  :docs
  (fn [db _]
    (:docs db)))

(reg-sub
  :translations
  (fn [db _]
    (:translations db)))

(reg-sub
  :language
  (fn [db _]
    (:language db)))

(reg-sub
  :theme
  (fn [db _]
    (:theme db)))

(reg-sub
  :identity
  (fn [db _]
    (:identity db)))

(reg-sub
  :user
  (fn [db _]
    (get-in db [:identity :user])))

(reg-sub
  :roles
  (fn [db _]
    (get-in db [:identity :roles])))