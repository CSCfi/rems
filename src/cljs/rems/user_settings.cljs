(ns rems.user-settings
  (:require [better-cond.core :as b]
            [clojure.string :as str]
            [goog.net.Cookies]
            [re-frame.core :as rf]
            [rems.common.roles :as roles]
            [rems.config]
            [rems.flash-message :as flash-message]
            [rems.globals]
            [rems.util :refer [fetch put!]]))

(def ^:private language-cookie-name "rems-user-preferred-language")
(def ^:private cookies (.getInstance goog.net.Cookies))

(defn get-language-cookie []
  (when-let [value (.get cookies language-cookie-name)]
    (keyword value)))

(defn- set-language-cookie! [language]
  (let [year-in-seconds (* 3600 24 365)]
    (.set cookies language-cookie-name (name language) year-in-seconds "/")))

(defn- update-css! [language]
  (doseq [element (array-seq (.getElementsByTagName js/document "link"))
          :when (str/includes? (.-href element) "screen.css")
          :let [cache-busting (re-find #"\?.*" (.-href element))]] ; preserve cache-busting query params if any
    (set! (.-href element)
          (str "/css/" (name language) "/screen.css" cache-busting))))

(defn- set-language! [lang]
  (set-language-cookie! lang)
  (set! (.. js/document -documentElement -lang) (name lang))
  (update-css! lang)
  (reset! rems.globals/language lang))

(defn- validate-lang [lang]
  (cond
    (empty? @rems.config/languages)
    nil

    (not (contains? (set @rems.config/languages) lang))
    nil

    :else
    lang))

(defn fetch-user-settings! []
  (when @roles/logged-in?
    (fetch "/api/user-settings"
           {:custom-error-handler? true
            :handler #(rf/dispatch-sync [:rems.user-settings/loaded-user-settings %])
            :error-handler #(flash-message/show-default-error! :top (str "Fetch user settings"))})))

(defn save-user-language! [lang]
  (b/cond
    :when (validate-lang lang)
    :do (set-language! lang)

    (not @roles/logged-in?)
    nil

    :else
    (put! "/api/user-settings/edit"
          {:params {:language lang}
           :handler #(fetch-user-settings!)
           :error-handler (flash-message/default-error-handler :top "Update user settings")})))

(rf/reg-event-fx
 :rems.user-settings/loaded-user-settings
 (fn [{:keys [db]} [_ user-settings]]
   (when (and (not (:user-settings db)) ; first time
              (not (validate-lang (get-language-cookie)))) ; e.g. unsupported language in cookie
     (save-user-language! (:language user-settings)))
   {:db (assoc db :user-settings user-settings)}))
