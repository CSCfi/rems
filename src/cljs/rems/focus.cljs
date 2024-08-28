(ns rems.focus
  "Focuses an HTML element as soon as it exists."
  (:require [rems.util :refer [get-bounding-client-rect get-dom-element on-element-appear]]))

(defn focus [el-or-selector & [opts]]
  (doto (get-dom-element el-or-selector)
    (.setAttribute "tabindex" "-1")
    (.focus (clj->js (or opts {})))
    (.removeAttribute "tabindex")))

(defn focus-without-scroll [el-or-selector]
  (focus el-or-selector (js-obj "preventScroll" true)))

(defn- get-distance-top
  "Returns distance to fixed navigation bar bottom (top-most element)."
  [el-or-selector]
  (+ (.-top (get-bounding-client-rect el-or-selector))
     (.-bottom (get-bounding-client-rect ".fixed-top"))))

(defn- scroll-below-navigation-menu
  "Scrolls an element into view if it's behind the navigation menu."
  [element]
  (let [distance (get-distance-top element)]
    (when (neg? distance)
      (.scrollBy js/window 0 distance))
    element))

(defn focus-and-ensure-visible [element]
  ;; Focusing the element scrolls it into the viewport, but
  ;; it can still be hidden behind the navigation menu.
  (focus element)
  (scroll-below-navigation-menu element))

(defn scroll-into-view
  "Attempts to scroll the window so that an element is in middle of the screen."
  [el-or-selector]
  (let [element (get-dom-element el-or-selector)
        available-height (.. js/window -screen -availHeight)]
    (.scrollBy js/window 0 (- (get-distance-top element)
                              (/ available-height 2)))
    element))

(defn scroll-into-view-and-focus [el-or-selector & [opts]]
  (cond
    (string? el-or-selector)
    (on-element-appear {:selector el-or-selector
                        :on-resolve #(-> % scroll-into-view (focus opts))})

    :else
    (-> el-or-selector
        scroll-into-view
        (focus opts))))

(defn scroll-offset
  "Scrolls the window so that an element stays in the same screen position
  before and after the page was re-rendered. The parameters of this function
  are the result of `getBoundingClientRect()` on the element before and after
  the re-rendering."
  [rect-before rect-after]
  (let [offset-before (.-top rect-before)
        offset-after (.-top rect-after)]
    (.scrollBy js/window 0 (- offset-after offset-before))))
