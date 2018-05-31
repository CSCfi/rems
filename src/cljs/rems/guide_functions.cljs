(ns rems.guide-functions
  (:require [clojure.string :as str]))

(defn- remove-indentation [docstring]
  (str/join "\n" (for [line (str/split (str "  " docstring) #"\n")]
                 (apply str (drop 2 line)))))

(defn render-component-info [title ns doc]
  [:div.component-info
   [:h3 ns "/" title]
   [:pre.example-source
    (if doc
      (remove-indentation doc)
      "No documentation available.")]])

(defn render-example [title src content]
  [:div.example
   [:h3 title]
   [:pre.example-source src]
   [:div.example-content content
    [:div.example-content-end]]])
