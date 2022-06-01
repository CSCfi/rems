(ns rems.hooks
  "Hooks can be added to REMS navigation.

  This namespace provides the default implementation, which does nothing.

  Useful for example to call 3rd party analytics scripts."
  (:refer-clojure :exclude [get]))

(defn ^:export get [])
(defn ^:export put [])
(defn ^:export navigate [])
