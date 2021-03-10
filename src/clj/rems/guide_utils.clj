(ns rems.guide-utils
  "Utilities for component guide: macro implementations"
  (:require [clojure.pprint :refer [code-dispatch write]]
            [rems.common.util :as common-util]))

(defmacro namespace-info [ns-symbol]
  (let [ns (find-ns ns-symbol)
        name (str (ns-name ns))
        meta (meta ns)
        meta (assoc meta
                    :file (common-util/normalize-file-path (:file meta))
                    :doc (-> &env :ns :doc))]
    `(rems.guide-utils/render-namespace-info
      ~name
      ~meta)))

(defmacro component-info [component]
  ;; TODO we'd like to use normalize-file-path on the component
  ;; metadata at compile time, just like we do for namespaces above.
  ;; However it seems that there's no way to get the metadata at
  ;; compile time (e.g. resolve and ns-resolve return nil). Thus we
  ;; normalize the path at runtime in rems.guide-utils/link-to-source.
  `(let [m# (meta (var ~component))]
     (rems.guide-utils/render-component-info
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
    `[rems.guide-utils/render-example ~title ~src (do ~@content)]))
