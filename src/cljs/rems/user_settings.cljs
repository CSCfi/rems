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


(defn- get-current-language [db]
  (let [available-languages (set (:languages (:config db)))
        validate (fn [language]
                   (when (contains? available-languages language)
                     language))]
    (or (validate (get-language-cookie))
        (validate (get-in db [:user-settings :language]))
        (:default-language db))))

(rf/reg-sub
 :language
 (fn [db _]
   (get-current-language db)))

(rf/reg-sub
 :languages
 (fn [db _]
   ;; default language first
   (sort (comp not= (:default-language db))
         (:languages db))))

(defn- update-css [language]
  ;; TODO retain cache-busting set by figwheel or our layout.clj
  (let [localized-css (str "/css/" (name language) "/screen.css")]
    ;; Figwheel replaces the linked stylesheet
    ;; so we need to search dynamically
    (doseq [element (array-seq (.getElementsByTagName js/document "link"))]
      (when (str/includes? (.-href element) "screen.css")
        ;; preserve cache-busting query params if any
        (let [cache-busting (re-find #"\?.*" (.-href element))]
          (set! (.-href element) (str localized-css cache-busting)))))))

(defn update-document-language [language]
  (set! (.. js/document -documentElement -lang) (name language))
  (update-css language))

(rf/reg-event-fx
 ::set-language
 (fn [{:keys [db]} [_ language]]
   (set-language-cookie! language)
   (update-document-language language)
   {:db (assoc-in db [:user-settings :language] language)
    :dispatch [::save-user-language! language]}))

(rf/reg-event-fx
 :check-if-should-save-language!
 (fn [{:keys [db]} _]
   (let [cookie-language (get-language-cookie)
         settings-language (get-in db [:user-settings :language])]
     ;; user can change the language before login, so we should persist
     ;; the change to the user settings after login
     (when (and cookie-language (not= cookie-language settings-language))
       {:dispatch [::save-user-language! cookie-language]}))))

(rf/reg-event-fx
 :loaded-user-settings
 (fn [{:keys [db]} [_ user-settings]]
   (let [first-time? (not (:user-settings db))]
     (update-document-language (:language user-settings))
     {:db (assoc db :user-settings user-settings)
      :dispatch-n (concat
                   (when first-time? ; to avoid an infinite loop if the settings fail to save (e.g. unsupported language in cookies)
                     [[:check-if-should-save-language!]]))})))

(defn fetch-user-settings! []
  (fetch "/api/user-settings"
         {:handler #(rf/dispatch-sync [:loaded-user-settings %])
          :error-handler (flash-message/default-error-handler :top "Fetch user settings")}))

(rf/reg-event-fx
 ::save-user-language!
 (fn [{:keys [db]} [_ language]]
   (let [user-id (get-in db [:identity :user :userid])]
     (when user-id
       (put! "/api/user-settings"
             {:params {:language language}
              :handler fetch-user-settings!
              :error-handler (flash-message/default-error-handler :top "Update user settings")}))
     {})))
