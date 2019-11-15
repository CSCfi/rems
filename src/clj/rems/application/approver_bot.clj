(ns rems.application.approver-bot
  (:require [clj-time.core :as time]))

(def bot-userid "approver-bot")

(defn- handler? [app userid]
  (contains? (->> (get-in app [:application/workflow :workflow.dynamic/handlers])
                  (mapv :userid)
                  set)
             userid))

(defn generate-commands [app]
  (when (and (handler? app bot-userid)
             (= :application.state/submitted (:application/state app))
             (empty? (:application/blacklist app)))
    [{:type :application.command/approve
      :actor bot-userid
      :time (time/now)
      :application-id (:application/id app)
      :comment ""}]))
