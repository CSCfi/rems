(ns rems.focus)

(def ^:private element-atom-to-be-focused (atom nil))

(defn- grab-focus []
  (when-let [element-atom @element-atom-to-be-focused]
    (when-let [element @element-atom]
      (.focus element)
      (reset! element-atom-to-be-focused nil))))

(defn set-focus [element-atom]
  (reset! element-atom-to-be-focused element-atom)
  (grab-focus))

(defn ref-changed [element-atom element]
  (reset! element-atom element)
  (grab-focus))
