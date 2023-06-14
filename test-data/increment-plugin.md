# Increment plugin

This plugin increments the `:value`. Useful for tests.

```clj
(defn transform [config data]
  (update data :value inc))

```
