(ns rems.hooks
  "Hooks can be added to REMS navigation.

  This namespace provides the default implementation, which does nothing.

  Useful for example to call 3rd party analytics scripts.

  See [docs/hooks.md]")

(defn on-get [& args]
  (when-let [hook (.. js/window -rems -hooks -get)]
    (apply hook args)))

(defn on-put [& args]
  (when-let [hook (.. js/window -rems -hooks -put)]
    (apply hook args)))

(defn on-navigate [& args]
  (when-let [hook (.. js/window -rems -hooks -navigate)]
    (apply hook args)))
