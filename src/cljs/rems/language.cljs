(ns rems.language
  (:require [clojure.string :as str]
            [re-frame.core :as rf :refer [reg-event-fx reg-sub]]))

(def ^:private language-cookie-name "rems-user-preferred-language")

(defn get-language-cookie []
  (when-let [value (.. js/document -cookie)]
    (keyword (second (str/split value #"=")))))

(defn- set-language-cookie! [language]
  (let [year-from-now (.setFullYear (js/Date.) (inc (.getFullYear (js/Date.))))]
    (set! (.. js/document -cookie)
          (str language-cookie-name "=" (name language) "; expires=" (.toUTCString year-from-now) "; path=/"))))

(reg-sub
 :language
 (fn [db _]
   (or (get-language-cookie)
       (get-in db [:user-settings :language])
       (:default-language db))))

(reg-sub
 :languages
 (fn [db _]
   ;; default language first
   (sort (comp not= (:default-language db))
         (:languages db))))

(reg-sub
 :default-language
 (fn [db _]
   (:default-language db)))

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

(reg-event-fx
 ::set-language
 (fn [_ [_ language]]
   (update-language language)
   {:dispatch [:rems.user-settings/update-user-settings {:language language}]}))
