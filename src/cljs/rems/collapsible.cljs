(ns rems.collapsible
  (:require [re-frame.core :as rf]
            [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- header
  [title]
  [:div.card-header
   [:span.card-title title]])

(defn- show-more-button
  [id expanded callback]
  [:div.collapse.collapse-toggle {:id (str id "more") :class (when-not expanded "show")}
   [:a.text-primary {:on-click #(do (.collapse (js/$ (str "#" id "collapse")) "show")
                                    (.collapse (js/$ (str "#" id "more")) "hide")
                                    (when callback
                                      (callback))
                                    (.collapse (js/$ (str "#" id "less")) "show"))}
    (text :t.collapse/show-more)]])

(defn- show-less-button
  [id expanded]
  [:div.collapse.collapse-toggle {:id (str id "less") :class (when expanded "show")}
   [:a.text-primary {:on-click #(do (.collapse (js/$ (str "#" id "collapse")) "hide")
                                    (.collapse (js/$ (str "#" id "more")) "show")
                                    (.collapse (js/$ (str "#" id "less")) "hide"))}
    (text :t.collapse/show-less)]
   ])

(defn- block [id expanded callback content-always content-hideable]
  [:div.collapse-content
   [:div content-always]
   (when-not (empty? content-hideable)
     [:div
      [:div.collapse {:id (str id "collapse") :class (when expanded "show")} content-hideable]
      [show-more-button id expanded callback]
      [show-less-button id expanded]])])

(defn component
  "Displays a collapsible block of content.

  Pass a map of options with the following keys:
  `:id` unique id required
  `:class` optional class for wrapper div
  `:open?` should the collapsible be open? Default false
  `:on-open` triggers the function callback given as an argument when load-more is clicked
  `:title` component displayed in title area
  `:always` component displayed always before collapsible area
  `:collapse` component that is toggled displayed or not"
  [{:keys [id class open? on-open title always collapse]}]
  [:div.collapse-wrapper {:id id
                          :class class}
   [header title]
   (when (or always collapse)
     [block id open? on-open always collapse])])

(defn guide
  []
  [:div
   (component-info component)
   (example "collapsible closed by default"
            [component {:id "hello2"
                        :title "Collapse minimized"
                        :always [:p "I am content that is always visible"]
                        :collapse [:p "I am content that you can hide"]}])
   (example "collapsible expanded by default"
            [component {:id "hello"
                        :open? true
                        :title "Collapse expanded"
                        :always [:p "I am content that is always visible"]
                        :collapse [:p "I am content that you can hide"]}])
   (example "collapsible without hideable content can't be opened"
            [component {:id "hello3"
                        :title "Collapse without children"
                        :always [:p "I am content that is always visible"]}])
   (example "collapsible that opens slowly"
            [component {:id "hello4"
                        :class "slow"
                        :title "Collapse expanded"
                        :always [:p "I am content that is always visible"]
                        :collapse (into [:div] (repeat 5 [:p "I am content that you can hide"]))}])])
