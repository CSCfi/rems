(ns rems.collapsible
  (:require [reagent.core :as r]
            [rems.text :refer [text]]
            [rems.guide-util :refer [component-info example]]))

(defn- show [id callback]
  (let [element (js/$ (str "#" id))]
    (.collapse element "show")
    (.focus element))
  ;; bootstrap's .collapse returns immediately, so in order to avoid
  ;; momentarily showing both buttons we wait for a hidden.bs.collapse event
  (.. (js/$ (str "." id "-more"))
      (collapse "hide")
      (one "hidden.bs.collapse" (fn [_] (. (js/$ (str "." id "-less")) collapse "show"))))
  (when callback
    (callback)))

(defn- hide [id callback]
  (.collapse (js/$ (str "#" id)) "hide")
  (.. (js/$ (str "." id "-less"))
      (collapse "hide")
      (one "hidden.bs.collapse" (fn [_]
                                  (. (js/$ (str "." id "-more")) collapse "show")
                                  (when callback
                                    (callback)))))
  (.focus (js/$ (str "#" id "-more-link"))))

(defn- header [{:keys [title class]}]
  [:h2.card-header.rems-card-margin-fix {:class (or class "rems-card-header")}
   title])

(defn- show-more-button [{:keys [label id expanded callback]}]
  [:a.collapse
   {:class [(str id "-more") (when-not expanded
                               "show")]
    :href "#"
    :id (str id "-more-link")
    :on-click (fn [event]
                (.preventDefault event)
                (show id callback))}
   label])

(defn- show-less-button [{:keys [label id expanded callback]}]
  [:a.collapse
   {:class [(str id "-less") (when expanded
                               "show")]
    :href "#"
    :on-click (fn [event]
                (.preventDefault event)
                (hide id callback))}
   label])

(defn controls
  "A hide/show button that toggles the visibility of a div. Arguments

  `id` id of the div to control. Should have the collapse class
  `label-show` label to show when div is collapsed
  `label-hide` label to show when div is expanded
  `open?` should the collapse be open initially?"
  [id label-show label-hide open?]
  [:<>
   [show-more-button {:label label-show
                      :id id
                      :expanded open?}]
   [show-less-button {:label label-hide
                      :id id
                      :expanded open?}]])

(defn- block [{:keys [open?]}]
  (let [show? (r/atom open?)] ; track internal open/closed status
    (fn [{:keys [id open? on-open on-close content-always content-hideable content-hidden content-footer top-less-button? bottom-less-button? class]}]
      (let [always? (not-empty content-always)
            hidden (not-empty content-hidden)
            show-more [:div.collapse-toggle
                       [show-more-button {:label (if (or always? hidden)
                                                   (text :t.collapse/show-more)
                                                   (text :t.collapse/show))
                                          :id id
                                          :expanded open?
                                          :callback (fn []
                                                      (reset! show? true)
                                                      (when on-open
                                                        (on-open)))}]]
            show-less [:div.collapse-toggle
                       [show-less-button {:label (if (or always? hidden)
                                                   (text :t.collapse/show-less)
                                                   (text :t.collapse/hide))
                                          :id id
                                          :expanded open?
                                          :callback (fn []
                                                      (reset! show? false)
                                                      (when on-close
                                                        (on-close)))}]]]
        [:div {:class class}
         content-always
         (when (seq content-hideable)
           [:div
            (when top-less-button? show-less)
            (when-not @show? hidden)
            [:div.collapse {:id id
                            :class (when open?
                                     "show")
                            :tab-index "-1"}
             content-hideable]
            show-more
            (when-not (false? bottom-less-button?) show-less)])
         content-footer]))))

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
  `:on-close` triggers the function callback given as an argument when show less is clicked
  `:title` component displayed in title area
  `:title-class` class for the title area
  `:always` component displayed always before collapsible area
  `:collapse` component that is toggled displayed or not
  `:collapse-hidden` component that is displayed when content is collapsed. Defaults nil
  `:footer` component displayed always after collapsible area"
  [{:keys [id class open? on-open on-close title title-class always collapse collapse-hidden footer top-less-button? bottom-less-button?]}]
  [:div {:id id :class class}
   (when title
     [header {:title title
              :class title-class}])
   (when (or always collapse footer)
     [block {:id (str id "-collapse")
             :open? open?
             :on-open on-open
             :on-close on-close
             :content-always always
             :content-hideable collapse
             :content-hidden collapse-hidden
             :content-footer footer
             :top-less-button? top-less-button?
             :bottom-less-button? bottom-less-button?}])])

(defn component
  "Displays a collapsible block of content.

  Pass a map of options with the following keys:
  `:id` unique id required
  `:class` optional class for wrapper div
  `:open?` should the collapsible be open? Default false
  `:top-less-button?` should top show less button be shown? Default false
  `:bottom-less-button?` should bottom show less button be shown? Default true
  `:on-open` triggers the function callback given as an argument when load-more is clicked
  `:on-close` triggers the function callback given as an argument when show less is clicked
  `:title` component displayed in title area
  `:title-class` class for the title area
  `:always` component displayed always before collapsible area
  `:collapse` component that is toggled displayed or not
  `:collapse-hidden` component that is displayed when content is collapsed. Defaults nil
  `:footer` component displayed always after collapsible area"
  [{:keys [id class open? on-open on-close title title-class always collapse collapse-hidden footer top-less-button? bottom-less-button?]}]
  [:div.collapse-wrapper {:id id
                          :class class}
   (when title
     [header {:title title
              :class title-class}])
   (when (or always collapse footer)
     [block {:id (str id "-collapse")
             :class "collapse-content"
             :open? open?
             :on-open on-open
             :on-close on-close
             :content-always always
             :content-hideable collapse
             :content-hidden collapse-hidden
             :content-footer footer
             :top-less-button? top-less-button?
             :bottom-less-button? bottom-less-button?}])])

(defn open-component
  "A helper for opening a collapsible/component or collapsible/minimal"
  [id]
  (show (str id "-collapse") nil))

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
   (example "collapsible that has different content when toggled"
            [component {:id "hello8"
                        :title "Collapsed"
                        :always [:p "I am content that is always visible"]
                        :collapse-hidden [:p "I am content that is only visible when collapsed"]
                        :collapse (into [:div] (repeat 15 [:p "I am long content that you can hide"]))}])
   (component-info minimal)
   (example "minimal collapsible without title"
            [minimal {:id "minimal1"
                      :always [:p "I am content that is always visible"]
                      :collapse (into [:div] (repeat 5 [:p "I am long content that you can hide"]))}])
   (example "minimal collapsible with custom border"
            [minimal {:id "minimal2"
                      :class "dashed-group m-1"
                      :always [:p "I am content that is always visible"]
                      :collapse (into [:div] (repeat 5 [:p "I am long content that you can hide"]))}])
   (example "minimal collapsible that opens slowly"
            [minimal {:id "minimal3"
                      :class "slow"
                      :title "Minimal expanded"
                      :always [:p "I am content that is always visible"]
                      :collapse (into [:div] (repeat 5 [:p "I am long content that you can hide"]))}])
   (example "minimal collapsible that has different content when toggled"
            [minimal {:id "minimal4"
                      :title "Minimal collapsed"
                      :always [:p "I am content that is always visible"]
                      :collapse-hidden [:p "I am content that is only visible when collapsed"]
                      :collapse (into [:div] (repeat 5 [:p "I am long content that you can hide"]))}])])
