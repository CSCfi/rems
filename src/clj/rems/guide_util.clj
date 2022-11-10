(ns rems.guide-util
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
    `(render-namespace-info
      ~name
      ~meta)))

(defmacro component-info [component]
  ;; TODO we'd like to use normalize-file-path on the component
  ;; metadata at compile time, just like we do for namespaces above.
  ;; However it seems that there's no way to get the metadata at
  ;; compile time (e.g. resolve and ns-resolve return nil). Thus we
  ;; normalize the path at runtime in link-to-source.
  `(let [m# (meta (var ~component))]
     (render-component-info
      (:name m#)
      (ns-name (:ns m#))
      m#)))


(defn- static-hiccup?
  "Does the content look like static hiccup markup?"
  [x]
  (and (vector? x)
       (keyword? (first x))
       (not= :<> (first x))
       (not= :code (first x))))

;; XXX: if we always use the last element as the example, we don't actually need this
(defn- clean-block
  "Remove a wrapping element from source.

  `:code` and `:<>` both work as wrappers.

  This can be used to prevent the code from looking like static hiccup markup."
  [x]
  (if (contains? #{:<> :code} (first x))
    (second x)
    x))

(defmacro example
  "Render an example of a component use into the guide.

  Captures the code into a preformatted element and shows it along with
  the rendered result.

  (example \"simple use\"
           [my-component \"hello\"])

  Static content can be added between code blocks with regular static hiccup markup.
  Also code such as definitions works. The last definition is used as the example.

  (example \"definition use\"
           [:p \"This is a very simple use case with simple data\"]
           (def data [1 2 3])
           [my-component \"hello\" data])

  Code can be wrapped to [:code ...] or [:<> ...] to appear as real code,
  and evaluated, unlike static hiccup. This is useful if you need to wrap
  the example in static hiccup because it needs a specific context.

  (example \"wrapped use\"
           [:code
            [:div.wrapper [my-component \"hello\"]]])
  "
  [title & content]
  (let [src (into [:div]
                  (for [block content]
                    (if (static-hiccup? block)
                      block
                      [:pre (with-out-str (write (clean-block block) :dispatch code-dispatch))])))]
    `(do ~@(butlast content)
         [render-example ~title ~src ~(last content)])))
