(ns rems.guide-macros
  "Utilities for component guide."
  (:require [clojure.pprint :refer [code-dispatch write]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(defn- normalize-file-path
  "The file path may contain local filesystem parts that we want to remove
  so that we can use the path to refer to e.g. project GitHub."
  [path]
  (str/replace (subs path (str/index-of path "src"))
               "\\" "/"))

(deftest normalize-file-path-test
  (is (= "src/foo/bar.clj" (normalize-file-path "/home/john/rems/src/foo/bar.clj")))
  (is (= "src/foo/bar.clj" (normalize-file-path "C:\\Users\\john\\rems\\src\\foo/bar.clj"))))

(defmacro namespace-info [ns-symbol]
  (let [ns (find-ns ns-symbol)
        name (str (ns-name ns))
        meta (meta ns)
        meta (assoc meta
                    :file (normalize-file-path (:file meta))
                    :doc (-> &env :ns :doc))]
    `(rems.guide-functions/render-namespace-info
      ~name
      ~meta)))

(comment
  (meta (find-ns 'rems.guide-macros)))

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
    `[rems.guide-functions/render-example ~title ~src (do ~@content)]))
