# HTTP request plugin

This plugin does a HTTP request. Useful for tests.

```clj
(require ['clj-http.client :as client])

(defn process [config data]
  (let [response (client/get "http://example.com/process/123"
                             {:accept :json
                              :headers "X-a-header" "value"})]
    response))

```
