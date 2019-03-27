(ns rems.poller.entitlements
  "Handing out entitlements for accepted applications. Stores
   entitlements in the db and optionally POSTs them to a webhook."
  (:require [clojure.test :refer :all]
            [mount.lite :as mount]
            [rems.db.applications :as applications]
            [rems.db.entitlements :as entitlements]
            [rems.poller.common :as common]))

(defn- entitlements-for-event [event]
  ;; we filter by event here, and by state in update-entitlements-for.
  ;; this is because update-entitlements-for is not actually
  ;; idempotent.
  (when (contains? #{:application.event/approved :application.event/closed} (:event/type event))
    (entitlements/update-entitlements-for (applications/get-dynamic-application-state (:application/id event)))))

(defn run []
  (common/run-event-poller ::poller entitlements-for-event))

(mount/defstate entitlements-poller
  :start (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
           (.scheduleWithFixedDelay run 10 10 java.util.concurrent.TimeUnit/SECONDS))
  :stop (doto @entitlements-poller
          (.shutdown)
          (.awaitTermination 60 java.util.concurrent.TimeUnit/SECONDS)))
