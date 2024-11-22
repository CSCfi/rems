(ns rems.app-hooks
  "Hooks can be added to REMS navigation.

  The default is to do nothing.

  Useful for example to call 3rd party analytics scripts.

  See [docs/hooks.md]"
  (:require [better-cond.core :as b]))

(defn on-get [& args]
  (b/when-let [hooks (.-rems_hooks js/window)
               hook (.-get hooks)]
    (apply hook args)))

(defn on-put [& args]
  (b/when-let [hooks (.-rems_hooks js/window)
               hook (.-put hooks)]
    (apply hook args)))

(defn on-navigate [& args]
  (b/when-let [hooks (.-rems_hooks js/window)
               hook (.-navigate hooks)]
    (apply hook args)))
