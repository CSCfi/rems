(ns rems.focus
  "Focuses an HTML element as soon as it exists.")

(defn focus-element-async
  "Focus an element when it appears. Options can include:
    :tries -- number of times to poll, defaults to 100
    :scroll? -- whether to .scollIntoView the element"
  [selector & [options]]
  (let [tries (get options :tries 100)
        scoll? (get options :scroll? false)]
    (when (pos? tries)
      (if-let [element (.querySelector js/document selector)]
        (do
          (.setAttribute element "tabindex" "-1")
          (.focus element)
          (when scroll?
            (.scrollIntoView element)))
        (js/setTimeout #(focus-element-async selector (assoc options :tries (dec tries)))
                       10)))))
