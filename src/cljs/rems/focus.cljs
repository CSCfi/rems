(ns rems.focus
  "Focuses an HTML element as soon as it exists."
  (:require [rems.util :refer [visibility-ratio]]))

(defn on-element-appear
  "Do something when an element appears."
  ([selector f]
   (on-element-appear selector f 100))
  ([selector f tries]
   (when (pos? tries)
     (if-let [element (.querySelector js/document selector)]
       (f element)
       (js/setTimeout #(on-element-appear selector f (dec tries))
                      10)))))

(defn- scroll-below-navigation-menu
  "Scrolls an element into view if it's behind the navigation menu."
  [element]
  (when-let [navbar (.querySelector js/document ".fixed-top")]
    (let [navbar-bottom (.-bottom (.getBoundingClientRect navbar))
          element-top (.-top (.getBoundingClientRect element))]
      (when (< element-top navbar-bottom)
        (.scrollBy js/window 0 (- element-top navbar-bottom))))))

(defn focus-and-ensure-visible [element]
  (.setAttribute element "tabindex" "-1")
  ;; Focusing the element scrolls it into the viewport, but
  ;; it can still be hidden behind the navigation menu.
  (.focus element)
  (scroll-below-navigation-menu element))

(defn focus-element-async
  "Focus an element when it appears."
  [selector]
  (on-element-appear selector focus-and-ensure-visible))
