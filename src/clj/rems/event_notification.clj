(ns rems.event-notification
  (:require [clj-http.client :as http]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.api.schema :as schema]
            [rems.config]
            [rems.db.applications :as applications]
            [rems.db.outbox :as outbox]
            [rems.json :as json]
            [rems.scheduler :as scheduler]
            [rems.util :refer [getx]]))

(def ^:private default-timeout 60)

(defn- notify! [target body]
  (try
    (let [timeout-ms (* 1000 (get target :timeout default-timeout))
          response (http/put (getx target :url)
                             {:body body
                              :throw-exceptions false
                              :content-type :json
                              :headers (get target :headers)
                              :socket-timeout timeout-ms
                              :conn-timeout timeout-ms})
          status (:status response)]
      (when-not (= 200 status)
        (log/error "Event notification response status" status)
        (str "failed: " status)))
    (catch Exception e
      (log/error "Event notification failed" e)
      "failed: exception")))

(defn process-outbox! []
  (doseq [entry (outbox/get-entries {:type :event-notification :due-now? true})]
    (if-let [error (notify! (get-in entry [:outbox/event-notification :target])
                            (get-in entry [:outbox/event-notification :body]))]
      (let [entry (outbox/attempt-failed! entry error)]
        (when (not (:outbox/next-attempt entry))
          (log/warn "all attempts to send event notification id " (:outbox/id entry) "failed")))
      (outbox/attempt-succeeded! (:outbox/id entry)))))

(mount/defstate event-notification-poller
  :start (scheduler/start! process-outbox! (.toStandardDuration (time/seconds 10)))
  :stop (scheduler/stop! event-notification-poller))

(defn- add-to-outbox! [target body]
  (outbox/put! {:outbox/type :event-notification
                :outbox/deadline (time/plus (time/now) (time/days 1)) ;; hardcoded for now
                :outbox/event-notification {:target target
                                            :body body}}))

(defn wants? [target event]
  (let [whitelist (:event-types target)]
    (or (empty? whitelist)
        (some? (some #{(:event/type event)} whitelist)))))

(deftest test-wants?
  (let [target {:url "whatever" :event-types [:application.event/submitted :application.event/approved]}]
    (is (false? (wants? target {:event/type :application.event/created})))
    (is (true? (wants? target {:event/type :application.event/submitted})))
    (is (true? (wants? target {:event/type :application.event/approved})))))

(defn queue-notifications! [events]
  (when-let [targets (seq (get rems.config/env :event-notification-targets))]
    (doseq [event events]
      (let [application (applications/get-application (:application/id event))
            event-with-app (assoc event :event/application application)
            body (json/generate-string event-with-app)]
        (doseq [target targets]
          (when (wants? target event)
            (add-to-outbox! target body)))))))
