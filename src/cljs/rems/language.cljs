(ns rems.language
  (:require [clojure.string :as str]
            [re-frame.core :as rf :refer [reg-event-fx reg-sub]]))

(reg-sub
 :language
 (fn [db _]
   (or (get-in db [:user-settings :language])
       (:language db)
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

(reg-event-fx
 ::set-language
 (fn [{:keys [db]} [_ language]]
   (let [user-id (get-in db [:identity :user :eppn])]
     (if user-id
       (rf/dispatch [:rems.user-settings/update-user-settings user-id {:language language}])
       {:db (assoc db :language language)}))))

(defn- update-css [language]
  (let [localized-css (str "/css/" (name language) "/screen.css")]
    ;; Figwheel replaces the linked stylesheet
    ;; so we need to search dynamically
    (doseq [element (array-seq (.getElementsByTagName js/document "link"))]
      (when (str/includes? (.-href element) "screen.css")
        (set! (.-href element) localized-css)))))

(defn update-language [language]
  (set! (.. js/document -documentElement -lang) language)
  (update-css language))
