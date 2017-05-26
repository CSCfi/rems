(ns rems.guide
  "Utilities for component guide."
  (:require [taoensso.tempura :as tempura]
            [rems.locales :as locales]))

(defmacro example [title content]
  `[:div.example
    [:h3 ~title]
    [:pre.example-source
     ~(with-out-str (clojure.pprint/write content :dispatch clojure.pprint/code-dispatch))]
    [:div.example-content ~content
     [:div.example-content-end]]])

(defmacro with-language [lang & body]
  `(binding [context/*lang* ~lang
             context/*tempura* (partial tempura/tr locales/tconfig [~lang])]
     ~@body))
