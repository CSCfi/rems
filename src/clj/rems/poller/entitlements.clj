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

(defn run []
  nil)

(mount/defstate entitlements-poller
  :start (scheduler/start! run (Duration/standardSeconds 10))
  :stop (scheduler/stop! entitlements-poller))
