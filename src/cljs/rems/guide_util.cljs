(ns rems.guide-util
  (:require [clojure.string :as str]
            [rems.common.util :as common-util]
            [rems.common.git :as git])
  (:require-macros rems.guide-util))

(defn- remove-indentation [docstring]
  (str/join "\n" (for [line (str/split (str "  " docstring) #"\n")]
                   (apply str (drop 2 line)))))

(defn- link-to-source [meta]
  (let [file (common-util/normalize-file-path (str "src/cljs/" (:file meta)))
        link-text (str file ":" (:line meta) ":" (:column meta))
        path (str file "#L" (:line meta))
        href (if-let [{:keys [revision]} git/+version+]
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


(def lipsum
  (str "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod "
       "tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim "
       "veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex "
       "ea commodo consequat. Duis aute irure dolor in reprehenderit in "
       "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur "
       "sint occaecat cupidatat non proident, sunt in culpa qui officia "
       "deserunt mollit anim id est laborum."))

(def lipsum-short "Lorem ipsum dolor sit amet")

(def lipsum-paragraphs
  "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam vehicula malesuada gravida. Nulla in massa eget quam porttitor consequat id egestas urna. Aliquam non pharetra dolor. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Sed quis ante at nunc convallis aliquet at quis ligula. Aliquam accumsan consectetur risus. Quisque semper turpis a erat dapibus iaculis.\n\nCras sit amet laoreet lectus. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Phasellus vestibulum a metus in laoreet. Phasellus eleifend eget dui vitae tincidunt. Aenean eu sapien sed nibh viverra facilisis in ac nulla. Integer quis odio eu sapien porta interdum in eu nulla. Sed sodales efficitur diam, vel iaculis ante bibendum vel. Praesent pretium ut lorem sit amet viverra. Etiam luctus nisi eget pharetra rutrum.\n\n")
