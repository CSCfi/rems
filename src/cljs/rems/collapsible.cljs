(ns rems.collapsible
  (:require [rems.text :refer [text]]
            [rems.guide-utils :refer [component-info example]]))

(defn- header
  [title title-class]
  [:h2.card-header {:class ["rems-card-margin-fix" (or title-class "rems-card-header")]} title])

(defn- show-more-button
  [label id expanded callback]
  [:div.mb-3.collapse.collapse-toggle {:class (str (str id "more ") (when-not expanded "show"))}
   [:a {:href "#"
        :id (str id "-more-link")
        :on-click (fn [event]
                    (.preventDefault event)
                    (let [element (js/$ (str "#" id "collapse"))]
                      (.collapse element "show")
                      (.focus element))
                    (.collapse (js/$ (str "." id "more")) "hide")
                    (when callback
                      (callback))
                    (.collapse (js/$ (str "." id "less")) "show"))}
    label]])

(defn- show-less-button
  [label id expanded]
  [:div.mb-3.collapse.collapse-toggle {:class (str (str id "less ") (when expanded "show"))}
   [:a {:href "#"
        :on-click (fn [event]
                    (.preventDefault event)
                    (.collapse (js/$ (str "#" id "collapse")) "hide")
                    (.collapse (js/$ (str "." id "more")) "show")
                    (.collapse (js/$ (str "." id "less")) "hide")
                    (.focus (js/$ (str "#" id "more-link"))))}
    label]])

(defn- block [id open? on-open content-always content-hideable content-footer top-less-button? bottom-less-button?]
  (let [always? (not-empty content-always)
        show-more [show-more-button
                   (if always?
                     (text :t.collapse/show-more)
                     (text :t.collapse/show))
                   id open? on-open]
        show-less [show-less-button
                   (if always?
                     (text :t.collapse/show-less)
                     (text :t.collapse/hide))
                   id open?]]
    [:div.collapse-content
     content-always
     (when (seq content-hideable)
       [:div
        (when top-less-button? show-less)
        [:div.collapse {:id (str id "collapse")
                        :class (when open? "show")
                        :tab-index "-1"}
         content-hideable]
        show-more
        (when-not (false? bottom-less-button?) show-less)])
     content-footer]))

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
  `:title-class` class for the title area
  `:always` component displayed always before collapsible area
  `:collapse` component that is toggled displayed or not
  `:footer` component displayed always after collapsible area"
  [{:keys [id class open? on-open title title-class always collapse footer top-less-button? bottom-less-button?]}]
  [:div {:id id :class class}
   (when title [header title title-class])
   (when (or always collapse footer)
     [block id open? on-open always collapse footer top-less-button? bottom-less-button?])])

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
  `:title-class` class for the title area
  `:always` component displayed always before collapsible area
  `:collapse` component that is toggled displayed or not
  `:footer` component displayed always after collapsible area"
  [{:keys [id class open? on-open title title-class always collapse footer top-less-button? bottom-less-button?]}]
  [:div.collapse-wrapper {:id id
                          :class class}
   (when title [header title title-class])
   (when (or always collapse footer)
     [block id open? on-open always collapse footer top-less-button? bottom-less-button?])])

(defn guide
  []
  [:div
   (component-info component)
   (example "collapsible closed by default"
            [component {:id "hello1"
                        :title "Collapse minimized"
                        :always [:p "I am content that is always visible"]
                        :collapse [:p "I am content that you can hide"]}])
   (example "collapsible expanded by default and footer"
            [component {:id "hello2"
                        :open? true
                        :title "Collapse expanded"
                        :always [:p "I am content that is always visible"]
                        :collapse [:p "I am content that you can hide"]
                        :footer [:p "I am the footer that is always visible"]}])
   (example "collapsible without title"
            [component {:id "hello3"
                        :open? true
                        :title nil
                        :always [:p "I am content that is always visible"]}])
   (example "collapsible without hideable content can't be opened"
            [component {:id "hello4"
                        :title "Collapse without children"
                        :always [:p "I am content that is always visible"]}])
   (example "collapsible without always content"
            [component {:id "hello5"
                        :title "Collapse without always content"
                        :collapse [:p "I am content that you can hide"]}])
   (example "collapsible that opens slowly"
            [component {:id "hello6"
                        :class "slow"
                        :title "Collapse expanded"
                        :always [:p "I am content that is always visible"]
                        :collapse (into [:div] (repeat 5 [:p "I am content that you can hide"]))}])
   (example "collapsible with two show less buttons"
            [component {:id "hello7"
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
