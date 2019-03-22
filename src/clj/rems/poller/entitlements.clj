(ns rems.poller.entitlements
  "Handing out entitlements for accepted applications. Stores
   entitlements in the db and optionally POSTs them to a webhook."
  (:require [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.entitlements :as entitlements]
            [rems.poller.common :as common]))

(defn- entitlements-for-event [event]
  (when (= :application.event/approved (:event/type event))
    (entitlements/update-entitlements-for (applications/get-dynamic-application-state (:application/id event)))))

(defn run []
  (common/run-event-poller ::poller entitlements-for-event))

(comment
  (common/get-poller-state ::poller)
  (common/set-poller-state! ::poller nil)
  (run))
