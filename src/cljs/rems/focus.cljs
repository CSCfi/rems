(ns rems.focus
  "Focuses an HTML element as soon as it exists."
  (:require [rems.util :refer [visibility-ratio]]))

(defn focus-element-async
  "Focus an element when it appears. Options can include:
    :tries -- number of times to poll, defaults to 100"
  [selector & [options]]
  (let [tries (get options :tries 100)]
    (when (pos? tries)
      (if-let [element (.querySelector js/document selector)]
        (do
          (.setAttribute element "tabindex" "-1")
          ;; Focusing the element scrolls it into the viewport, but
          ;; it's hidden behind the navigation menu,
          ;; so explicit scrolling is needed. There used to be code
          ;; for this, but most pages perform a scroll to top
          ;; anyway (e.g. via ::rems.spa/user-triggered-navigation).
          (.focus element))
        (js/setTimeout #(focus-element-async selector (assoc options :tries (dec tries)))
                       10)))))
