# Exception plugin

This plugin throws an exception when used. Useful for tests.

```clj
(defn transform [config data]
  (throw (Exception. "hello from plugin")))

```
