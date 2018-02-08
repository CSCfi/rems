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
  :user
  (fn [db _]
    (:user db)))

(reg-sub
  :active-role
  (fn [db _]
    (:active-role db)))

(reg-sub
  :catalogue
  (fn [db _]
    (:catalogue db)))

(reg-sub
  :application
  (fn [db _]
    (:application db)))
