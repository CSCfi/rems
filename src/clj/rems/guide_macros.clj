(ns rems.guide-macros
  "Utilities for component guide."
  (:require [clojure.pprint :refer [code-dispatch write]]))

(defmacro component-info [component]
  `(let [m# (meta (var ~component))]
     (rems.guide-functions/render-component-info
      (:name m#)
      (ns-name (:ns m#))
      m#)))


(defn- static-hiccup?
  "Does the content look like static hiccup markup?"
  [x]
  (and (vector? x)
       (keyword? (first x))))

(defmacro example
  "Render an example of a component use into the guide.

  Captures the code into a preformatted element and shows it along with
  the rendered result.

  (example \"simple use\"
           [my-component \"hello\"])

  Static content can be added between code blocks with regular static hiccup markup.

  (example \"simple use\"
           [:p \"This is a very simple use case with no data\"]
           [my-component \"hello\" []]
           [:p \"Which can be written also like this\"]
           [my-component \"hello\"])"
  [title & content]
  (let [src (into [:div]
                  (for [block content]
                    (if (static-hiccup? block)
                      block
                      [:pre (with-out-str (write block :dispatch code-dispatch))])))]
    `(rems.guide-functions/render-example ~title ~src (do ~@content))))
