(ns rems.guide
  "Utilities for component guide."
  (:require [clojure.string :as s]
            [rems.locales :as locales]
            [taoensso.tempura :as tempura]))

(defn- remove-indentation [docstring]
  (s/join "\n" (for [line (s/split (str "  " docstring) #"\n")]
                 (apply str (drop 2 line)))))

(defn render-component-info [title ns doc]
  [:div.component-info
   [:h3 ns "/" title]
   [:pre.example-source
    (if doc
      (remove-indentation doc)
      "No documentation available.")]])

(defmacro component-info [component]
  `(let [m# (meta (var ~component))]
     (rems.guide/render-component-info
      (:name m#)
      (ns-name (:ns m#))
      (:doc m#)
      )))

(defn render-example [title src content]
  [:div.example
   [:h3 title]
   [:pre.example-source src]
   [:div.example-content content
    [:div.example-content-end]]])

(defmacro example
  ([title content]
   (let [src (with-out-str (clojure.pprint/write content :dispatch clojure.pprint/code-dispatch))]
     `(rems.guide/render-example ~title ~src ~content))))


(defmacro with-language [lang & body]
  `(binding [context/*lang* ~lang
             context/*tempura* (partial tempura/tr locales/tconfig [~lang])]
     ~@body))
