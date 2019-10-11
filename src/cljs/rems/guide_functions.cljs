(ns rems.guide-functions
  (:require [clojure.string :as str]))

(def ^:private +rems-github-master+ "https://github.com/CSCfi/rems/tree/master")

(defn- remove-indentation [docstring]
  (str/join "\n" (for [line (str/split (str "  " docstring) #"\n")]
                   (apply str (drop 2 line)))))

(defn render-namespace-info [title meta]
  (let [source (str (:file meta) ":" (:line meta) ":" (:column meta))
        href (str +rems-github-master+ "/" (:file meta) "#L" (:line meta))
        doc (:doc meta)]
    [:div.namespace-info
     [:h3 title [:small " (" [:a {:href href} source] ")"]]
     [:pre.example-source
      (if doc
        (remove-indentation doc)
        "No documentation available.")]]))

(defn render-component-info [title ns meta]
  (let [source (str (:file meta) ":" (:line meta) ":" (:column meta))
        href (str +rems-github-master+ "/" (:file meta) "#L" (:line meta))
        doc (:doc meta)]
    [:div.component-info
     [:h3 ns "/" title [:small " (" [:a {:href href} source] ")"]]
     [:pre.example-source
      (if doc
        (remove-indentation doc)
        "No documentation available.")]]))

(defn render-example [title src content]
  [:div.example
   [:h3 title]
   [:div.example-source src]
   [:div.example-content content
    [:div.example-content-end]]])
