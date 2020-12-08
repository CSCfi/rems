(ns rems.language-switcher
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.guide-functions]
            [rems.text :refer [text-format]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn lang-link-classes [current-language language]
  (if (= current-language language)
    "btn btn-link active"
    "btn btn-link"))

(defn language-switcher
  "Language switcher widget"
  []
  (let [current-language @(rf/subscribe [:language])
        languages @(rf/subscribe [:languages])]
    (when (> (count languages) 1)
      (into [:div.language-switcher]
            (for [language languages]
              (let [lang-str (str/upper-case (name language))]
                [:form.inline
                 [:button {:type :button
                           :class (str (lang-link-classes current-language language) " lang-link-classes")
                           :on-click (fn []
                                       (rf/dispatch [:rems.user-settings/set-language language])
                                       (rf/dispatch [:rems.spa/user-triggered-navigation]))
                           :aria-label (text-format :t.navigation/change-language lang-str)
                           :data-toggle "collapse"
                           :data-target ".navbar-collapse.show"}
                  lang-str]]))))))

(defn guide []
  [:div
   (component-info language-switcher)
   (example "language-switcher"
            [language-switcher])])
