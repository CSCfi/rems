(ns rems.focus
  "Focuses an HTML element as soon as it exists."
  (:require [medley.core :refer [assoc-some]]
            [re-frame.core :as rf]
            [rems.common.util :refer [andstr]]
            [rems.util :refer [on-element-appear]]))

(defn focus [element]
  (doto element
    (.setAttribute "tabindex" "-1")
    (.focus)
    (.removeAttribute "tabindex")))

(defn focus-selector [selector]
  (focus (.querySelector js/document selector)))

(defn focus-without-scroll [element]
  (doto element
    (.setAttribute "tabindex" "-1")
    (.focus (js-obj "preventScroll" true))
    (.removeAttribute "tabindex")))

(defn- scroll-below-navigation-menu
  "Scrolls an element into view if it's behind the navigation menu."
  [element]
  (when-let [navbar (.querySelector js/document ".fixed-top")]
    (let [navbar-bottom (.-bottom (.getBoundingClientRect navbar))
          element-top (.-top (.getBoundingClientRect element))]
      (when (< element-top navbar-bottom)
        (.scrollBy js/window 0 (- element-top navbar-bottom))))))

(defn focus-and-ensure-visible [element]
  ;; Focusing the element scrolls it into the viewport, but
  ;; it can still be hidden behind the navigation menu.
  (focus element)
  (scroll-below-navigation-menu element))

(defn scroll-to-top
  "Scrolls an element to the top of the window (but below the navigation menu)"
  [element]
  (let [navbar (.querySelector js/document ".fixed-top")
        _ (assert navbar)
        navbar-bottom (.-bottom (.getBoundingClientRect navbar))
        element-top (.-top (.getBoundingClientRect element))]
    (.scrollBy js/window 0 (- element-top navbar-bottom))))

(defn scroll-offset
  "Scrolls the window so that an element stays in the same screen position
  before and after the page was re-rendered. The parameters of this function
  are the result of `getBoundingClientRect()` on the element before and after
  the re-rendering."
  [rect-before rect-after]
  (let [offset-before (.-top rect-before)
        offset-after (.-top rect-after)]
    (.scrollBy js/window 0 (- offset-after offset-before))))

(defn scroll-into-view-el [^js element opts]
  (.scrollIntoView element (clj->js opts)))

(defn scroll-into-view [selector & [opts]]
  (on-element-appear {:selector selector
                      :on-resolve #(scroll-into-view-el % (or opts {}))}))

(rf/reg-fx ::on-element-appear (fn [opts] (on-element-appear opts)))

(rf/reg-event-fx ::focus-input (fn [_ [_ {:keys [selector target]}]]
                                 {::on-element-appear (-> {:selector (str (andstr selector " ") ":is(textarea, input)")
                                                           :on-resolve focus}
                                                          (assoc-some :target target))}))
