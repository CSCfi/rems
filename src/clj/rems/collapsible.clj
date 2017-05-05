(ns rems.collapsible
  (:require [rems.guide :refer :all]))

(defn header
  [href expanded aria-controls title]
  [:h3.card-header
   [:a.card-title (merge {:data-toggle "collapse" :data-parent "#accordion" :href href :aria-expanded expanded :aria-controls aria-controls}
                         (when-not expanded {:class "collapsed"}))
    title]])

(defn block [id expanded content]
  (let [classes (str "collapse" (when expanded " show"))]
    [:div {:id id :class classes}
     content]))

(defn guide
  []
  (list (example "Collapsible component expanded by default"
           (list
             [:div#accordion
              (header "#hello" true "hello" "Collapse expanded")
              (block "hello" true [:p "I am content"])]))
        (example "Collapsible component closed by default"
           (list
             [:div#accordion
              (header "#hello2" false "hello2" "Collapse minimized")
              (block "hello2" false [:p "I am content"])]))))
