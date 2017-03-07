(ns rems.language-switcher
  (:require [rems.context :as context]
            [compojure.core :refer [defroutes POST]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :refer [redirect]]))

;; languages to switch between hardcoded for now
(def ^:private +languages+ ["en" "fi"])

(defroutes switcher-routes
  (POST "/language/:language"
        {session :session
         {language :language} :params
         {referer "referer"} :headers}
        (assoc (redirect referer :see-other)
               :session (assoc session :language (keyword language)))))

(defn language-switcher
  "Language switcher widget"
  []
  [:div.language-switcher
   (for [lang +languages+]
     [:form {:method "post" :action (str "/language/" lang)}
      (anti-forgery-field)
      [:button.btn-link.nav-link {:type "submit"} lang]])])
