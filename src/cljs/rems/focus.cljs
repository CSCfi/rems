(ns rems.focus
  "Focuses an HTML element as soon as it exists.")

(defn focus-element-async
  ([selector]
   (focus-element-async selector 100))
  ([selector tries]
   (when (pos? tries)
     (if-let [element (.querySelector js/document selector)]
       (do
         (.setAttribute element "tabindex" "-1")
         (.focus element))
       (js/setTimeout #(focus-element-async selector (dec tries))
                      10)))))
