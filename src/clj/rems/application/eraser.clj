(ns rems.application.eraser
  (:require [mount.core :as mount]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [rems.scheduler :as scheduler]
            [rems.db.applications :as applications]
            [rems.config :refer [env]])
  (:import [org.joda.time Period]))

(defn- is-expired?
  [application]
  (let [last-activity (:application/last-activity application)
        state (:application/state application)
        expiration-threshold (some-> env :application-expiration state Period/parse)]
    (when expiration-threshold
      (time/before? last-activity (time/minus (time/now) expiration-threshold)))))

(deftest test-is-expired
  (with-redefs [env {:application-expiration {:application.state/draft "P90D"
                                              :application.state/closed "P7D"}}]
    (testing "should identify expired application by state"
      (let [over-90d-ago (time/minus (time/now) (time/days 90) (time/seconds 1))
            over-7d-ago (time/minus (time/now) (time/days 7) (time/seconds 1))
            over-1d-ago (time/minus (time/now) (time/days 1) (time/seconds 1))]
        (is (true? (is-expired? {:application/state :application.state/draft
                                 :application/last-activity over-90d-ago})))
        (is (false? (is-expired? {:application/state :application.state/draft
                                  :application/last-activity over-7d-ago})))
        (is (true? (is-expired? {:application/state :application.state/closed
                                 :application/last-activity over-7d-ago})))
        (is (false? (is-expired? {:application/state :application.state/closed
                                  :application/last-activity over-1d-ago})))
        (is (nil? (is-expired? {:application/state :application.state/rejected
                                :application/last-activity over-90d-ago})))))))

(defn remove-expired-applications!
  []
  (log/info :start #'remove-expired-applications!)
  (doseq [app-id (->> (applications/get-all-unrestricted-applications)
                      (filter is-expired?)
                      (map :application/id))]
    (try
      (applications/delete-application! app-id)
      (log/info "Succesfully removed application" app-id)
      (catch Throwable t
        (log/error "Failed to remove application" app-id ":" t))))
  (applications/reload-cache!)
  (log/info :finish #'remove-expired-applications!))

(mount/defstate expired-application-poller
  :start (when (:application-expiration env)
           (scheduler/start! remove-expired-applications! (.toStandardDuration (time/days 1))))
  :stop (scheduler/stop! expired-application-poller))

(comment
  (mount/defstate expired-application-poller-test
    :start (scheduler/start! (fn [] (with-redefs [env {:application-expiration {:application.state/draft "P90D"}}]
                                      (remove-expired-applications!))) (.toStandardDuration (time/seconds 10)))
    :stop (scheduler/stop! expired-application-poller-test))
  (mount/start #{#'expired-application-poller-test})
  (mount/stop #{#'expired-application-poller-test}))

