# Validate attributes

A plugin to validate that specified attributes are present and not empty strings.

```clj
(require '[clojure.string :as str])
(require '[clojure.tools.logging :as log])

(defn empty-attributes [user attributes]
  (for [{:keys [attribute-name error-key]} attributes
        :let [attribute-value (get user (keyword attribute-name))
              error? (str/blank? attribute-value)]
        :when error?]
    error-key))

(defn validate [config data]
  (when-some [invalid-attributes (empty-attributes data (get config :required-attributes))]
    (log/info invalid-attributes)
    [{:key :t.login.errors/invalid-user
      :args invalid-attributes}]))

```
