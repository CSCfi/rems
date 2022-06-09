(ns rems.identity
  (:require [re-frame.core :as rf]
            [rems.common.roles :as roles]))

;;; subscriptions

(rf/reg-sub
 :identity
 (fn [db _]
   (:identity db)))

(rf/reg-sub
 :user
 (fn [db _]
   (get-in db [:identity :user])))

(rf/reg-sub
 :roles
 (fn [db _]
   (get-in db [:identity :roles])))

(rf/reg-sub
 :logged-in
 (fn [db _]
   (roles/is-logged-in? (get-in db [:identity :roles]))))

;;; handlers

(rf/reg-event-fx
 :set-identity
 (fn [{:keys [db]} [_ identity]]
   {:db (assoc db :identity identity)
    :dispatch [:rems.user-settings/fetch-user-settings]}))

(rf/reg-event-db
 :set-roles
 (fn [db [_ roles]]
   (assoc-in db [:identity :roles] (set roles))))
