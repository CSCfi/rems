# Fail neg plugin

This plugin validates that the ``:value` is positive`. Useful for tests.

```clj
(defn validate [config data]
  (when-not (pos? (:value data))
    [{:result :fail :reason :neg}])) ; could return multiple errors

```
