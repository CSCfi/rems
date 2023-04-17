# Fail neg plugin

This plugin validates that the ``:value` is positive`. Useful for tests.

```clj
(when-not (pos? (:value data))
  [{:result :fail :reason :neg}]) ; could return multiple errors

```
