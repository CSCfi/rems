(ns rems.collapsible
  (:require [rems.guide-functions]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- header
  [title]
  [:div.card-header.rems-card-header
   [:span.card-title title]])

(defn- show-more-button
  [id expanded callback]
  [:div.mb-3.collapse.collapse-toggle {:class (str (str id "more ") (when-not expanded "show"))}
   [:a.text-primary {:on-click #(do (.collapse (js/$ (str "#" id "collapse")) "show")
                                    (.collapse (js/$ (str "." id "more")) "hide")
                                    (when callback
                                      (callback))
                                    (.collapse (js/$ (str "." id "less")) "show"))}
    (text :t.collapse/show-more)]])

(defn- show-less-button
  [id expanded]
  [:div.mb-3.collapse.collapse-toggle {:class (str (str id "less ") (when expanded "show"))}
   [:a.text-primary {:on-click #(do (.collapse (js/$ (str "#" id "collapse")) "hide")
                                    (.collapse (js/$ (str "." id "more")) "show")
                                    (.collapse (js/$ (str "." id "less")) "hide"))}
    (text :t.collapse/show-less)]])

(defn- block [id expanded callback content-always content-hideable top-less-button? bottom-less-button?]
  [:div.collapse-content
   content-always
   (when-not (empty? content-hideable)
     [:div
      (when top-less-button? [show-less-button id expanded])
      [:div.collapse {:id (str id "collapse") :class (when expanded "show")} content-hideable]
      [show-more-button id expanded callback]
      (when-not (false? bottom-less-button?) [show-less-button id expanded])])])

(defn minimal
  "Displays a minimal collapsible block of content.

  The difference to `component` is that there are no borders around the content.

  Pass a map of options with the following keys:
  `:id` unique id required
  `:class` optional class for wrapper div
  `:open?` should the collapsible be open? Default false
  `:top-less-button?` should top show less button be shown? Default false
  `:bottom-less-button?` should bottom show less button be shown? Default true
  `:on-open` triggers the function callback given as an argument when load-more is clicked
  `:title` component displayed in title area
  `:always` component displayed always before collapsible area
  `:collapse` component that is toggled displayed or not"
  [{:keys [id class open? on-open title always collapse top-less-button? bottom-less-button?]}]
  [:div {:id id :class class}
   (when title [header title])
   (when (or always collapse)
     [block id open? on-open always collapse top-less-button? bottom-less-button?])])

(defn component
  "Displays a collapsible block of content.

  Pass a map of options with the following keys:
  `:id` unique id required
  `:class` optional class for wrapper div
  `:open?` should the collapsible be open? Default false
  `:top-less-button?` should top show less button be shown? Default false
  `:bottom-less-button?` should bottom show less button be shown? Default true
  `:on-open` triggers the function callback given as an argument when load-more is clicked
  `:title` component displayed in title area
  `:always` component displayed always before collapsible area
  `:collapse` component that is toggled displayed or not"
  [{:keys [id class open? on-open title always collapse top-less-button? bottom-less-button?]}]
  [:div.collapse-wrapper {:id id
                          :class class}
   (when title [header title])
   (when (or always collapse)
     [block id open? on-open always collapse top-less-button? bottom-less-button?])])

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
   (example "collapsible without title"
            [component {:id "hello5"
                        :open? true
                        :title nil
                        :always [:p "I am content that is always visible"]}])
   (example "collapsible without hideable content can't be opened"
            [component {:id "hello3"
                        :title "Collapse without children"
                        :always [:p "I am content that is always visible"]}])
   (example "collapsible that opens slowly"
            [component {:id "hello4"
                        :class "slow"
                        :title "Collapse expanded"
                        :always [:p "I am content that is always visible"]
                        :collapse (into [:div] (repeat 5 [:p "I am content that you can hide"]))}])
   (example "collapsible with two show less buttons"
            [component {:id "hello5"
                        :class "slow"
                        :title "Collapse expanded"
                        :always [:p "I am content that is always visible"]
                        :top-less-button? true
                        :collapse (into [:div] (repeat 15 [:p "I am long content that you can hide"]))}])
   (component-info minimal)
   (example "minimal collapsible without title"
            [component {:id "minimal1"
                        :always [:p "I am content that is always visible"]
                        :collapse (into [:div] (repeat 5 [:p "I am long content that you can hide"]))}])
   (example "minimal collapsible with custom border"
            [component {:id "minimal2"
                        :class "form-item"
                        :always [:p "I am content that is always visible"]
                        :collapse (into [:div] (repeat 5 [:p "I am long content that you can hide"]))}])
   (example "minimal collapsible"
            [component {:id "minimal3"
                        :class "slow"
                        :title "Minimal expanded"
                        :always [:p "I am content that is always visible"]
                        :collapse (into [:div] (repeat 5 [:p "I am long content that you can hide"]))}])])
