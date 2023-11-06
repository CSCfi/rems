(ns rems.application.approver-bot
  (:require [clj-time.core :as time]
            [rems.common.application-util :as application-util]
            [rems.service.application :as application]))

(defn- should-approve? [application]
  (empty? (:application/blacklist application)))

(defn- generate-commands [event application]
  (when (and (= :application.event/submitted (:event/type event)) ;; approver bot only reacts to fresh applications
             (application-util/is-handler? application "approver-bot")
             (should-approve? application))
    [{:type :application.command/approve
      :actor "approver-bot"
      :time (time/now)
      :application-id (:application/id event)
      :comment ""}]))

(defn run-approver-bot [new-events]
  (doall (mapcat #(generate-commands % (application/get-full-internal-application (:application/id %)))
                 new-events)))
