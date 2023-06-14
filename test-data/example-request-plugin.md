# HTTP request plugin

This plugin does a HTTP request. Useful for tests.

```clj
(require '[clj-http.client :as client])

(defn process [config data]
  (assert (:url config))
  (assert (:value data))
  (let [response (client/post (:url config)
                              {:accept :json
                               :form-params (select-keys data [:value])
                               :headers {"x-a-header" (:value data)}})]
    response))

```
