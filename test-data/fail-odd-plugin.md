# Fail odd plugin

This plugin validates that the ``:value` is even`. Useful for tests.

```clj
(defn validate [config data]
  (when (odd? (:value data))
    [{:result :fail :reason :odd}])) ; could return multiple errors

```
