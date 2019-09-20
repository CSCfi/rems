(ns rems.user-settings
  (:require [re-frame.core :as rf]
            [rems.flash-message :as flash-message]
            [rems.language :as language]
            [rems.util :refer [fetch put!]]))

(rf/reg-event-fx
 :loaded-user-settings
 (fn [{:keys [db]} [_ user-settings]]
   (let [new-language (or (language/get-language-cookie)
                          (:language user-settings))
         new-settings (assoc user-settings :language new-language)]
     (language/update-language new-language)
     ;; if the user has changed language before login
     ;; the cookie will not match the language setting
     ;; and we should save the new language setting
     (when (and new-language (not= new-language (:language user-settings)))
       (rf/dispatch [::update-user-settings new-settings]))
     {:db (assoc db :user-settings new-settings)})))

(defn fetch-user-settings! []
  (fetch "/api/user-settings"
         {:handler #(rf/dispatch [:loaded-user-settings %])
          :error-handler (flash-message/default-error-handler :top "Fetch user settings")}))

(rf/reg-event-fx
 ::update-user-settings
 (fn [{:keys [db]} [_ settings]]
   (let [user-id (get-in db [:identity :user :eppn])
         new-settings (merge (:user-settings db) settings)]
     (when user-id
       (put! "/api/user-settings"
             {:params new-settings
              :error-handler (flash-message/default-error-handler :top "Update user settings")}))
     {:db (assoc db :user-settings new-settings)})))
