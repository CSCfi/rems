(ns rems.event-notification
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [rems.api.schema :as schema]
            [rems.config]
            [rems.json :as json]))

(defn notify! [events]
  (doseq [event events]
    (let [body (json/generate-string event)]
      (when-let [targets (seq (get rems.config/env :event-notification-targets))]
        (doseq [target targets]
          (try
            ;; TODO: switch to PUT, which is harder to test because of a bug in stub-http
            (http/post target
                       {:body body
                        :content-type :json
                        :socket-timeout 2500
                        :conn-timeout 2500})
            (catch Exception e
              (log/error "Event notification failed" e))))))))
