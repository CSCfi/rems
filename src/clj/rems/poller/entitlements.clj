(ns rems.poller.entitlements
  "Handing out entitlements for accepted applications. Stores
   entitlements in the db and optionally POSTs them to a webhook."
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.api.applications-v2 :as applications-v2]
            [rems.db.entitlements :as entitlements]
            [rems.poller.common :as common])
  (:import [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

(defn- entitlements-for-event [event]
  ;; we filter by event here, and by state in update-entitlements-for.
  ;; this is for performance reasons only
  (when (contains? #{:application.event/approved
                     :application.event/licenses-accepted
                     :application.event/member-removed
                     :application.event/closed}
                   (:event/type event))
    (let [application (applications-v2/get-unrestricted-application (:application/id event))]
      (entitlements/update-entitlements-for application))))

(defn run []
  (common/run-event-poller ::poller entitlements-for-event))

(mount/defstate entitlements-poller
  :start (doto (ScheduledThreadPoolExecutor. 1)
           (.scheduleWithFixedDelay run 10 10 TimeUnit/SECONDS))
  :stop (doto entitlements-poller
          (.shutdownNow)
          (.awaitTermination 60 TimeUnit/SECONDS)))
