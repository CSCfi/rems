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
  (log/info "Sending event notification for event" (select-keys body [:event/id :application/id :event/type :event/time])
            "to" (:url target))
  (try
    (let [timeout-ms (* 1000 (get target :timeout default-timeout))
          response (http/put (getx target :url)
                             {:body (json/generate-string body)
                              :throw-exceptions false
                              :content-type :json
                              :headers (get target :headers)
                              :socket-timeout timeout-ms
                              :conn-timeout timeout-ms})
          status (:status response)]

      (if (= 200 status)
        (log/infof "Sent event notification for event %s -> %s" (select-keys body [:event/id :application/id :event/type :event/time]) status)
        (do
          (log/error "Event notification response status" status)
          (str "failed: " status))))

    (catch Exception e
      (log/error "Event notification failed" e)
      "failed: exception")))

(defn process-outbox! []
  ;; TODO if we want to guarantee event ordering, we need fetch all
  ;; outbox entries here, and pick the one with the lowest outbox id
  ;; or event id, and do nothing if it isn't due yet.
  ;;
  ;; This can be done per target url or globally.
  (log/debug "Trying to send notifications")
  (let [due-notifications (outbox/get-due-entries :event-notification)]
    (log/debug (str "Notifications due: " (count due-notifications)))
    (doseq [entry due-notifications]
      (if-let [error (notify! (get-in entry [:outbox/event-notification :target])
                              (get-in entry [:outbox/event-notification :body]))]
        (let [entry (outbox/attempt-failed! entry error)]
          (when-not (:outbox/next-attempt entry)
            (log/warn "all attempts to send event notification id " (:outbox/id entry) "failed")))
        (outbox/attempt-succeeded! (:outbox/id entry))))))

(mount/defstate event-notification-poller
  :start (scheduler/start! "event-notification-poller" process-outbox! (.toStandardDuration (time/seconds 10)))
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
    (is (true? (wants? target {:event/type :application.event/approved}))))
  (let [target {:url "whatever" :event-types []}]
    (is (true? (wants? target {:event/type :application.event/created})))
    (is (true? (wants? target {:event/type :application.event/submitted})))
    (is (true? (wants? target {:event/type :application.event/approved})))))

(defn queue-notifications! [events]
  (when-let [targets (seq (get rems.config/env :event-notification-targets))]
    (doseq [event events
            :let [application-part (delay {:event/application (applications/get-application (:application/id event))})]
            target targets
            :when (wants? target event)
            :let [body (merge event
                              (when (:send-application? target true)
                                @application-part))]]
      (add-to-outbox! target body))))
