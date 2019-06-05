(ns rems.focus)

(def ^:private element-atom-to-focus (atom nil))

(defn- grab-focus []
  (when-let [element-atom @element-atom-to-focus]
    (when-let [element @element-atom]
      (.focus element)
      (reset! element-atom-to-focus nil))))

(defn set-focus [element-atom]
  (reset! element-atom-to-focus element-atom)
  (grab-focus))

(defn ref-changed [element-atom element]
  (reset! element-atom element)
  (grab-focus))
