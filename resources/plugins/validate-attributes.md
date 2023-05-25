# Validate attributes

A plugin to validate that specified attributes are present and not empty strings.

```clj
(require '[clojure.string :as str])

(defn validate [config data]
  (for [{:keys [attribute-name error-key]} (get config :required-attributes)
        :let [error? (str/blank? (get data (keyword attribute-name)))]
        :when error?]
    error-key))

```
