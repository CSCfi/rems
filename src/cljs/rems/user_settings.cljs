(ns rems.user-settings
  (:require [clojure.string :as str]
            [goog.net.cookies]
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


(defn get-language [db]
  (or (get-language-cookie)
      (get-in db [:user-settings :language])
      (:default-language db)))

(rf/reg-sub
 :language
 (fn [db _]
   (get-language db)))

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
  (update-css language))

(rf/reg-event-fx
 ::set-language
 (fn [{:keys [db]} [_ language]]
   (update-language language)
   {:db (assoc-in db [:user-settings :language] language)
    :dispatch [::save-user-language!]}))

(rf/reg-event-fx
 :check-if-should-save-language!
 (fn [{:keys [db]} _]
   (let [current-language (get-language db)
         settings-language (get-in db [:user-settings :language])]
     ;; user can change language before login
     ;; so we should sometimes save the language
     ;; to the profile after login
     (when (and current-language (not= current-language settings-language))
       {:dispatch [::save-user-language!]}))))

(rf/reg-event-fx
 :loaded-user-settings
 (fn [{:keys [db]} [_ user-settings]]
   {:db (assoc db :user-settings user-settings)
    :dispatch [:check-if-should-save-language!]}))

(defn fetch-user-settings! []
  (fetch "/api/user-settings"
         {:handler #(rf/dispatch-sync [:loaded-user-settings %])
          :error-handler (flash-message/default-error-handler :top "Fetch user settings")}))

(rf/reg-event-fx
 ::save-user-language!
 (fn [{:keys [db]} [_]]
   (let [user-id (get-in db [:identity :user :userid])]
     (when user-id
       (put! "/api/user-settings"
             {:params {:language (get-language db)}
              :handler fetch-user-settings!
              :error-handler (flash-message/default-error-handler :top "Update user settings")}))
     {})))
