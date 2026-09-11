(ns rems.language-switcher
  (:require [clojure.string :as str]
            [rems.config]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text-format text]]
            [rems.user-settings]))

(defn lang-link-classes [current-language language]
  (if (= current-language language)
    "btn btn-link active"
    "btn btn-link"))

(defn get-language-name [language]
  (or (text (keyword (name :t.language-names) (name language)))
      (str/upper-case (name language))))

(defn language-button [language]
  [:button {:type :button
            :class (lang-link-classes @rems.config/current-language language)
            :on-click #(rems.user-settings/save-user-language! language)
            :aria-label (text-format :t.navigation/change-language
                                     (get-language-name language))
            :data-toggle "collapse"
            :data-target ".navbar-collapse.show"}
   (get-language-name language)])

(defn language-switcher
  "Language switcher widget"
  []
  (let [languages @rems.config/languages]
    (when (> (count languages) 1)
      (into [:div.language-switcher]
            (for [language languages]
              (let [lang-str (str/upper-case (name language))]
                [:form.inline
                 (language-button language)]))))))

(defn guide []
  [:div
   (component-info language-switcher)
   (example "language-switcher"
            [language-switcher])])
