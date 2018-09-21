(ns rems.guide-macros
  "Utilities for component guide."
  (:require [clojure.pprint :refer [code-dispatch write]]
            [rems.context :as context]
            [rems.locales :as locales]
            [taoensso.tempura :as tempura]))

(defmacro component-info [component]
  `(let [m# (meta (var ~component))]
     (rems.guide-functions/render-component-info
      (:name m#)
      (ns-name (:ns m#))
      (:doc m#))))

(defmacro example
  ([title content]
   (let [src (with-out-str (write content :dispatch code-dispatch))]
     `(rems.guide-functions/render-example ~title ~src ~content))))

(defmacro with-language [lang & body]
  `(binding [context/*lang* ~lang
             context/*tempura* (partial tempura/tr locales/tconfig [~lang])]
     ~@body))
