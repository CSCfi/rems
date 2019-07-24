(ns rems.language-switcher
  (:require [clojure.string :as str]
            [re-frame.core :as rf :refer [reg-event-fx reg-event-db]]
            [rems.guide-functions]
            [rems.status-modal :as status-modal])
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
                           :class (lang-link-classes current-language language)
                           :on-click (fn []
                                       (rf/dispatch [:rems.language/set-language language])
                                       (rf/dispatch [:rems.spa/user-triggered-navigation]))}
                  lang-str]]))))))

(defn guide []
  [:div
   (component-info language-switcher)
   (example "language-switcher"
            [language-switcher])])
