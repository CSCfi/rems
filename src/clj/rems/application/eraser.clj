(ns rems.application.eraser
  (:require [mount.core :as mount]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [rems.scheduler :as scheduler]
            [rems.db.applications :as applications]
            [rems.config :refer [env]])
  (:import [org.joda.time Period]))

(defn- is-expired-draft?
  [application]
  (let [expiration-threshold (-> env :application-expiration :draft Period/parse)
        last-activity (:application/last-activity application)
        state (:application/state application)]
    (and (= :application.state/draft state)
         (time/before? last-activity (time/minus (time/now) expiration-threshold)))))

(deftest test-is-expired-draft
  (with-redefs [env {:application-expiration {:draft "P90D"}}]
    (testing "should identify expired draft application"
      (is (true? (is-expired-draft? {:application/state :application.state/draft
                                     :application/last-activity (time/minus (time/now) (time/days 90) (time/seconds 1))})))
      (is (false? (is-expired-draft? {:application/state :application.state/draft
                                      :application/last-activity (time/now)})))
      (is (false? (is-expired-draft? {:application/state :application.state/submitted
                                      :application/last-activity (time/minus (time/now) (time/days 90))}))))))

(defn remove-expired-applications! []
  (log/info "Start rems.application.eraser/remove-expired-applications!")
  (doseq [app-id (->> (applications/get-all-unrestricted-applications)
                      (filter is-expired-draft?)
                      (map :application/id))]
    (try
      (applications/remove-application-data! app-id)
      (log/info "Succesfully removed application" app-id)
      (catch Throwable t
        (log/error "Failed to remove application" app-id ":" t))))
  (applications/reload-cache!))

(mount/defstate expired-draft-poller
  :start (when (:application-expiration env)
           (scheduler/start! remove-expired-applications! (.toStandardDuration (time/days 1))))
  :stop (scheduler/stop! expired-draft-poller))

(comment
  (mount/defstate expired-draft-poller-test
    :start (scheduler/start! (fn [] (with-redefs [env {:application-expiration {:draft "P90D"}}]
                                      (remove-expired-applications!))) (.toStandardDuration (time/seconds 10)))
    :stop (scheduler/stop! expired-draft-poller-test))
  (mount/start #{#'expired-draft-poller-test})
  (mount/stop #{#'expired-draft-poller-test}))

