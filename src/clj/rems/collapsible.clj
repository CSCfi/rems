(ns rems.collapsible
  (:require [rems.guide :refer :all]))

(defn- header
  [id expanded title openable?]
  [:div.card-header (when openable?
                      {:class "clickable"
                       :onclick (str "$('#" id "').collapse('toggle')")})
   (if openable?
     [:a.card-title (merge {:data-toggle "collapse" :data-parent "#accordion" :href (str "#" id) :aria-expanded expanded :aria-controls id}
                           (when-not expanded {:class "collapsed"}))
      title]
     [:span.card-title title])])

(defn- block [id expanded content]
  (let [classes (str "collapse" (when expanded " show"))]
    [:div.collapse-content {:id id :class classes}
     content]))

(defn component [id expanded title content]
  (let [openable? (not-empty content)]
    [:div.collapse-wrapper
     (header id expanded title openable?)
     (block id expanded content)]))

(defn guide
  []
  (list (example "Collapsible component expanded by default"
                 (list
                  [:div#accordion
                   (component "hello" true "Collapse expanded" [:p "I am content"])]))
        (example "Collapsible component closed by default"
                 (list
                  [:div#accordion
                   (component "hello2" false "Collapse minimized" [:p "I am content"])]))))
