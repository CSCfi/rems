(ns rems.application.approver-bot
  (:require [clj-time.core :as time]
            [rems.application-util :as application-util]
            [rems.db.applications :as applications]))

(def bot-userid "approver-bot")

(defn- should-approve? [application]
  (empty? (:application/blacklist application)))

(defn- generate-commands [event application]
  (when (= :application.event/submitted (:event/type event)) ;; approver bot only reacts to fresh applications
    (when (application-util/is-handler? application bot-userid)
      (when (should-approve? application)
        [{:type :application.command/approve
          :actor bot-userid
          :time (time/now)
          :application-id (:application/id event)
          :comment ""}]))))

(defn run-approver-bot [new-events]
  (doall (mapcat #(generate-commands % (applications/get-unrestricted-application (:application/id %)))
                 new-events)))
