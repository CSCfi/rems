(ns rems.language-switcher
  (:require [compojure.core :refer [POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.context :as context]
            [ring.util.response :refer [redirect]]))

(def +default-language+ :en)

;; languages to switch between hardcoded for now
(def ^:private +languages+ ["en" "fi"])

(defroutes switcher-routes
  (POST "/language/:language"
        {session :session
         {language :language} :params
         {referer "referer"} :headers}
        (assoc (redirect referer :see-other)
               :session (assoc session :language (keyword language)))))

(defn lang-link-classes [lang]
  (if (= context/*lang* (keyword lang))
    "btn-link active"
    "btn-link"))

(defn language-switcher
  "Language switcher widget"
  []
  [:div.language-switcher
   (for [lang +languages+]
     [:form.inline {:method "post" :action (str "/language/" lang)}
      (anti-forgery-field)
      [:button {:class (lang-link-classes lang) :type "submit"} lang]])])
