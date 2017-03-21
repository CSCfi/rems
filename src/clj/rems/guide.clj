(ns rems.guide
  "Utilities for component guide."
  (:require [taoensso.tempura :as tempura]
            [rems.locales :as locales]))

(defn example [name content]
  [:div.example
   [:h3 name]
   [:div.example-content content
    [:div.example-content-end]]])

(defmacro with-language [lang & body]
  `(binding [context/*lang* ~lang
             context/*tempura* (partial tempura/tr locales/tconfig [~lang])]
     ~@body))
