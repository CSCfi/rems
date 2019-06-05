(ns rems.focus)

(def autofocus (atom nil))

(defn grab-focus []
  (when-let [element-atom @autofocus]
    (when-let [element @element-atom]
      (.focus element)
      (reset! autofocus nil))))

(defn set-focus [element-atom]
  (reset! autofocus element-atom)
  (grab-focus))

(defn ref-changed [element-atom element]
  (reset! element-atom element)
  (grab-focus))
