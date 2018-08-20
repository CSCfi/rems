(ns rems.language-switcher
  (:require [re-frame.core :as rf]
            [rems.guide-functions])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn lang-link-classes [current-language language]
  (if (= current-language language)
    "btn-link active"
    "btn-link"))

(defn language-switcher
  "Language switcher widget"
  []
  (let [current-language @(rf/subscribe [:language])
        languages @(rf/subscribe [:languages])]
    (into [:div.language-switcher]
          (for [language languages]
            [:form.inline
             #_(anti-forgery-field)
             [:button {:class (lang-link-classes current-language language) :type "button"
                       :on-click #(rf/dispatch [:set-current-language language])} language]]))))

(defn guide []
  [:div
   (component-info language-switcher)
   (example "language-switcher"
            [language-switcher])])
