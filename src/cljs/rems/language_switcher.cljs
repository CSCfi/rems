(ns rems.language-switcher
  (:require [re-frame.core :as rf])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;; languages to switch between hardcoded for now
(def ^:private +languages+ [:en :fi])

(defn language-switcher
  "Language switcher widget"
  []
  [:div.language-switcher
   (for [lang +languages+]
     [:form.inline
      #_(anti-forgery-field)
      [:button {:class "btn-link" :type "button"
                :on-click #(rf/dispatch [:set-current-language lang])} lang]])])

(defn guide []
  [:div
   (component-info language-switcher)
   (example "language-switcher"
            [language-switcher])])
