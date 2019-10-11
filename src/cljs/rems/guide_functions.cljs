(ns rems.guide-functions
  (:require [clojure.string :as str]
            [rems.git :as git])
  (:require-macros [rems.read-gitlog :refer [read-current-version]]))

(defn- remove-indentation [docstring]
  (str/join "\n" (for [line (str/split (str "  " docstring) #"\n")]
                   (apply str (drop 2 line)))))

(defn- link-to-source [meta]
  (let [link-text (str (:file meta) ":" (:line meta) ":" (:column meta))
        path (str (:file meta) "#L" (:line meta))
        href (if-let [{:keys [revision]} (read-current-version)]
               (str git/+tree-url+ revision "/" path)
               (str git/+master-url+ path))]
    [:a {:href href} link-text]))

(defn- docstring [meta]
  [:pre.docstring
   (if-let [doc (:doc meta)]
     (remove-indentation doc)
     "No documentation available.")])

(defn render-namespace-info [title meta]
  [:div.namespace-info
   [:h3 title [:small " (" (link-to-source meta) ")"]]
   [docstring meta]])

(defn render-component-info [title ns meta]
  [:div.component-info
   [:h3 ns "/" title [:small " (" (link-to-source meta) ")"]]
   [docstring meta]])

(defn render-example [title src content]
  [:div.example
   [:h3 title]
   [:div.example-source src]
   [:div.example-content content
    [:div.example-content-end]]])
