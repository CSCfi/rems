(ns rems.guide-macros
  "Utilities for component guide."
  (:require [clojure.pprint :refer [code-dispatch write]]))

(defmacro component-info [component]
  `(let [m# (meta (var ~component))]
     (rems.guide-functions/render-component-info
      (:name m#)
      (ns-name (:ns m#))
      m#)))

(defmacro example
  ([title content]
   (let [src (with-out-str (write content :dispatch code-dispatch))]
     `(rems.guide-functions/render-example ~title ~src ~content))))
