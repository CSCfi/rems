(ns rems.poller.entitlements
  "Handing out entitlements for accepted applications. Stores
   entitlements in the db and optionally POSTs them to a webhook."
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.db.applications :as applications]
            [rems.db.entitlements :as entitlements]
            [rems.poller.common :as common]
            [rems.scheduler :as scheduler])
  (:import [org.joda.time Duration]))

(defn- entitlements-for-event [event]
  ;; performance improvement: filter events which may affect entitlements
  (when (contains? #{:application.event/approved
                     :application.event/closed
                     :application.event/licenses-accepted
                     :application.event/member-removed
                     :application.event/resources-changed
                     :application.event/revoked}
                   (:event/type event))
    (let [application (applications/get-unrestricted-application (:application/id event))]
      (entitlements/update-entitlements-for application))))

(defn run []
  (common/run-event-poller ::poller entitlements-for-event))

(mount/defstate entitlements-poller
  :start (scheduler/start! run (Duration/standardSeconds 10))
  :stop (scheduler/stop! entitlements-poller))
