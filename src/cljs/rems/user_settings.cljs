(ns rems.user-settings
  (:require [clojure.string :as str]
            [goog.net.cookies :as cookies]
            [re-frame.core :as rf]
            [rems.flash-message :as flash-message]
            [rems.util :refer [fetch put!]]))

(def ^:private language-cookie-name "rems-user-preferred-language")

(defn get-language-cookie []
  (when-let [value (.get goog.net.cookies language-cookie-name)]
    (keyword value)))

(defn- set-language-cookie! [language]
  (let [year-in-seconds (* 3600 24 365)]
    (.set goog.net.cookies language-cookie-name (name language) year-in-seconds "/")))

(rf/reg-sub :language (fn [db _] (get-in db [:user-settings :language] (:default-language db))))

(rf/reg-sub
 :languages
 (fn [db _]
   ;; default language first
   (sort (comp not= (:default-language db))
         (:languages db))))

(defn- update-css [language]
  (let [localized-css (str "/css/" (name language) "/screen.css")]
    ;; Figwheel replaces the linked stylesheet
    ;; so we need to search dynamically
    (doseq [element (array-seq (.getElementsByTagName js/document "link"))]
      (when (str/includes? (.-href element) "screen.css")
        (set! (.-href element) localized-css)))))

(defn update-language [language]
  (set! (.. js/document -documentElement -lang) (name language))
  (set-language-cookie! language)
  (rf/dispatch [:rems.flash-message/reset]) ; may have saved localized content
  (update-css language))

(rf/reg-event-fx
 ::set-language
 (fn [{:keys [db]} [_ language]]
   (update-language language)
   {:db (assoc-in db [:user-settings :language] language)
    :dispatch [:rems.user-settings/save-user-settings]}))

(rf/reg-event-fx
 :loaded-user-settings
 (fn [{:keys [db]} [_ user-settings]]
   (let [new-language (or (get-language-cookie)
                          (:language user-settings))
         new-settings (assoc user-settings :language new-language)]
     (update-language new-language)
     {:db (assoc db :user-settings new-settings)
     ;; if the user has changed language before login
     ;; the cookie will not match the language setting
     ;; and we should save the new language setting
      :dispatch (when (and new-language (not= new-language (:language user-settings))) [::save-user-settings!])})))

(defn fetch-user-settings! []
  (fetch "/api/user-settings"
         {:handler #(rf/dispatch [:loaded-user-settings %])
          :error-handler (flash-message/default-error-handler :top "Fetch user settings")}))

(rf/reg-event-fx
 ::save-user-settings!
 (fn [{:keys [db]} [_]]
   (let [user-id (get-in db [:identity :user :eppn])
         new-settings (:user-settings db)]
     (when user-id
       (put! "/api/user-settings"
             {:params new-settings
              :error-handler (flash-message/default-error-handler :top "Update user settings")}))
     {:db (assoc db :user-settings new-settings)})))
