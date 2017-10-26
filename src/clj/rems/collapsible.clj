(ns rems.collapsible
  (:require [rems.guide :refer :all]
            [rems.text :refer :all]))

(defn- header
  [id expanded title]
  [:div.card-header
   [:span.card-title title]])

(defn- block [id expanded content]
  [:div.collapse-content
   [:div.collapse {:id id :class (when expanded "show")}
    content]
   [:div.collapse.collapse-toggle {:id (str id "more") :class (when-not expanded "show")}
    [:a.text-primary {:onclick (str "$('#" id "').collapse('show'), $('#" id "more').collapse('hide'), $('#" id "less').collapse('show')")}
     (text :t.collapse/show-more)]]
   [:div.collapse.collapse-toggle {:id (str id "less") :class (when expanded "show")}
    [:a.text-primary {:onclick (str "$('#" id "').collapse('hide'), $('#" id "more').collapse('show'), $('#" id "less').collapse('hide')")}
     (text :t.collapse/show-less)]
    ]])

(defn component [id expanded title content]
  [:div.collapse-wrapper
   (header id expanded title)
   (when content
     (block id expanded content))])

(defn guide
  []
  (list (example "collapsible expanded by default"
                 (component "hello" true "Collapse expanded" [:p "I am content"]))
        (example "collapsible closed by default"
                 (component "hello2" false "Collapse minimized" [:p "I am content"]))
        (example "collapsible without children can't be opened"
                 (component "hello3" false "Collapse without children" nil))))
