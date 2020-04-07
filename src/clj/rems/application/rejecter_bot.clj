(ns rems.application.rejecter-bot
  (:require [clj-time.core :as time]
            [rems.common.application-util :as application-util]
            [rems.db.applications :as applications]))

(def bot-userid "rejecter-bot")

(defn- should-reject? [application]
  (not (empty? (:application/blacklist application))))

(defn- generate-commands [event application]
  (when (and (= :application.event/submitted (:event/type event)) ;; rejecter bot only reacts to fresh applications
             (application-util/is-handler? application bot-userid)
             (should-reject? application))
    [{:type :application.command/reject
      :application-id (:application/id application)
      :time (time/now)
      :comment ""
      :actor bot-userid}]))

(defn run-rejecter-bot [new-events]
  (doall (mapcat #(generate-commands % (applications/get-application-raw (:application/id %)))
                 new-events)))
