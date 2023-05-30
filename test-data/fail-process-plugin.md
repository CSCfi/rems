# Fail process plugin

This plugin fails processing. Useful for tests.

```clj
(defn process [config data]
  [{:result :fail}]) ; could return multiple errors

```
