# Validate group membership

Check that the specified group attribute contains at least
one of the valid groups.

```clj
(require '[rems.config :refer [env]])
(require '[clojure.string :as str])
(require '[clojure.tools.logging :as log])

(defn validate [config data]
  (let [{:keys [attribute-name valid-groups error-key]} config
        groups (get data (keyword attribute-name))]

    (when (:log-authentication-details env)
      (log/info "Groups" groups))

    (when (or (empty? groups)
              (empty? (clojure.set/intersection (set groups) valid-groups)))
      [error-key])))

```
