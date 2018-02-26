(ns rems.collapsible
  (:require [rems.guide :refer :all]
            [rems.text :refer :all]))

(defn- header
  [title]
  [:div.card-header
   [:span.card-title title]])

(defn- show-more-button
  [id expanded]
  [:div.collapse.collapse-toggle {:id (str id "more") :class (when-not expanded "show")}
   [:a.text-primary {:onclick (str "$('#" id "collapse').collapse('show'), $('#" id "more').collapse('hide'), $('#" id "less').collapse('show')")}
    (text :t.collapse/show-more)]])

(defn- show-less-button
  [id expanded]
  [:div.collapse.collapse-toggle {:id (str id "less") :class (when expanded "show")}
   [:a.text-primary {:onclick (str "$('#" id "collapse').collapse('hide'), $('#" id "more').collapse('show'), $('#" id "less').collapse('hide')")}
    (text :t.collapse/show-less)]
   ])

(defn- block [id expanded content-always content-hideable]
  [:div.collapse-content
   [:div content-always]
   (when-not (empty? content-hideable)
     (list
      [:div.collapse {:id (str id "collapse") :class (when expanded "show")} content-hideable]
      (show-more-button id expanded)
      (show-less-button id expanded)))])

(defn component [{:keys [id class open? title always collapse]}]
  [:div.collapse-wrapper {:id id
                          :class class}
   (header title)
   (when (or always collapse)
     (block id open? always collapse))])
