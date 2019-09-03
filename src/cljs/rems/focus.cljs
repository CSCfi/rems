(ns rems.focus
  "Focuses an HTML element as soon as it exists."
  (:require [rems.util :refer [visibility-ratio]]))

(defn focus-element-async
  "Focus an element when it appears. Options can include:
    :tries -- number of times to poll, defaults to 100
    :scroll? -- whether to .scrollIntoView the element"
  [selector & [options]]
  (let [tries (get options :tries 100)
        scroll? (get options :scroll? false)]
    (when (pos? tries)
      (if-let [element (.querySelector js/document selector)]
        (do
          (.setAttribute element "tabindex" "-1")
          (.focus element)
          ;; We use block: center because the default block: top
          ;; often leaves the flash message obscured by the fixed-top
          ;; navbar. This means that we also need to explicitly check
          ;; visibility to avoid unnecessary scrolling (e.g. on the
          ;; application page where the flash message floats and is
          ;; always visible).
          ;;
          ;; TODO visibility-ratio can be 1.0 but the element can be
          ;; behind the navbar, but this is requires careful
          ;; positioning.
          (when scroll?
            (when (> 0.9 (visibility-ratio element))
              (.scrollIntoView element (clj->js {:block :center})))))
        (js/setTimeout #(focus-element-async selector (assoc options :tries (dec tries)))
                       10)))))
