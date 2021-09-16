(ns rems.application.cleaner
  (:require [mount.core :as mount]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [rems.scheduler :as scheduler]
            [rems.db.applications :as applications]
            [rems.config :refer [env]]))

(defn- is-expired-draft?
  [application]
  (let [expiration-threshold (:draft-application-expiration-threshold env)
        last-activity (:application/last-activity application)
        state (:application/state application)]
    (and (= :application.state/draft state)
         (time/before? last-activity (time/minus (time/now) (time/days expiration-threshold))))))

(deftest test-is-expired-draft
  (with-redefs [env {:draft-application-expiration-threshold 90}]
    (testing "should identify expired draft application"
      (are [expected input] (= expected (is-expired-draft? input))
        true {:application/state :application.state/draft
              :application/last-activity (time/minus (time/now) (time/days 90) (time/seconds 1))}
        false {:application/state :application.state/draft
               :application/last-activity (time/now)}
        false {:application/state :application.state/submitted
               :application/last-activity (time/minus (time/now) (time/days 90) (time/seconds 1))}))))

(defn process-expired-draft-applications! []
  (let [removed-ids (atom [])
        failed-ids (atom [])]
    (doseq [app-id (->> (applications/get-all-unrestricted-applications)
                        (filter is-expired-draft?)
                        (map :application/id))]
      (try
        (applications/remove-application-data! app-id)
        (swap! removed-ids conj app-id)
        (catch Throwable t
          (log/error (str "Removing application with id " app-id " failed: ") t)
          (swap! failed-ids conj app-id))))
    (when (seq @failed-ids)
      (log/info "process-expired-draft-applications! failed to remove applications" @failed-ids))
    (when (seq @removed-ids)
      (log/info "process-expired-draft-applications! succesfully removed applications" @removed-ids))
    (applications/reload-cache!)))

(when (:enable-expired-draft-application-processing env)
  (mount/defstate expired-draft-poller
    :start (scheduler/start! process-expired-draft-applications! (.toStandardDuration (time/days 1)))
    :stop (scheduler/stop! expired-draft-poller)))

(comment
  (mount/defstate expired-draft-poller-test
    :start (scheduler/start! process-expired-draft-applications! (.toStandardDuration (time/seconds 10)))
    :stop (scheduler/stop! expired-draft-poller-test))
  (mount/start #{#'expired-draft-poller-test})
  (mount/stop #{#'expired-draft-poller-test}))