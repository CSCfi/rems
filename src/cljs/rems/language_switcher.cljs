(ns rems.language-switcher
  (:require [clojure.string :as str]
            [rems.config]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text-format]]
            [rems.user-settings]))

(defn lang-link-classes [current-language language]
  (if (= current-language language)
    "btn btn-link active"
    "btn btn-link"))

(defn language-switcher
  "Language switcher widget"
  []
  (let [languages @rems.config/languages]
    (when (> (count languages) 1)
      (into [:div.language-switcher]
            (for [language languages]
              (let [lang-str (str/upper-case (name language))]
                [:form.inline
                 [:button {:type :button
                           :class (lang-link-classes @rems.config/language-or-default language)
                           :on-click #(rems.user-settings/save-user-language! language)
                           :aria-label (text-format :t.navigation/change-language lang-str)
                           :data-toggle "collapse"
                           :data-target ".navbar-collapse.show"}
                  lang-str]]))))))

(defn guide []
  [:div
   (component-info language-switcher)
   (example "language-switcher"
            [language-switcher])])
