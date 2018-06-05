(ns rems.handlers
  (:require [rems.db :as db]
            [rems.util :refer [dispatch!]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx]]))

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

(reg-event-fx
 :landing-page-redirect!
 (fn [{:keys [db]}]
   (if (get-in db [:identity :roles])
     (let [roles (set (get-in db [:identity :roles]))]
       (println "Selecting landing page based on roles" roles)
       (.removeItem js/window.sessionStorage "rems-redirect-url")
       (cond
         (contains? roles :owner) (dispatch! "/#/administration")
         (contains? roles :approver) (dispatch! "/#/actions")
         (contains? roles :reviewer) (dispatch! "/#/actions")
         :else (dispatch! "/#/catalogue"))
       {})
     {:dispatch [:landing-page-redirect!]})))
