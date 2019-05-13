(ns rems.language-switcher
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.guide-functions])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn lang-link-classes [current-language language]
  (if (= current-language language)
    "nav-link active"
    "nav-link"))

(defn language-switcher
  "Language switcher widget"
  []
  (let [current-language @(rf/subscribe [:language])
        languages @(rf/subscribe [:languages])]
    (into [:div.language-switcher]
          (for [language languages]
            (let [lang-str (str/upper-case (name language))]
              [:form.inline
               [:a {:href "#"
                    :on-click (fn [event]
                                (.preventDefault event)
                                (rf/dispatch [:set-current-language language]))
                    :class (lang-link-classes current-language language)
                    :style {:padding 0}}
                lang-str]])))))

(defn guide []
  [:div
   (component-info language-switcher)
   (example "language-switcher"
            [language-switcher])])
