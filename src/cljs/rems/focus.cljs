(ns rems.focus
  "Focuses an HTML element as soon as it exists.")

(defn focus [element]
  (.setAttribute element "tabindex" "-1")
  (.focus element))

(defn focus-selector [selector]
  (focus (.querySelector js/document selector)))

(defn focus-without-scroll [element]
  (.setAttribute element "tabindex" "-1")
  (.focus element (js-obj "preventScroll" true)))

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
  ;; Focusing the element scrolls it into the viewport, but
  ;; it can still be hidden behind the navigation menu.
  (focus element)
  (scroll-below-navigation-menu element))

(defn focus-element-async
  "Focus an element when it appears."
  [selector]
  (on-element-appear selector focus-and-ensure-visible))

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
