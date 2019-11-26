(ns rems.application.approver-bot
  (:require [clj-time.core :as time]
            [rems.db.applications :as applications]))

(def bot-userid "approver-bot")

(defn- handler? [app userid]
  (contains? (->> (get-in app [:application/workflow :workflow.dynamic/handlers])
                  (mapv :userid)
                  set)
             userid))

(defn- generate-commands [app]
  (when (and (handler? app bot-userid)
             (= :application.state/submitted (:application/state app))
             (empty? (:application/blacklist app)))
    [{:type :application.command/approve
      :actor bot-userid
      :time (time/now)
      :application-id (:application/id app)
      :comment ""}]))

(defn- generate-commands-for-application-id [app-id]
  (generate-commands (applications/get-unrestricted-application app-id)))

(defn run-approver-bot [new-events]
  ;; the copy-as-new command produces events for multiple applications, so there can be 1 or 2 app-ids
  (let [app-ids (->> new-events
                     (filter (fn [event]
                               ;; performance optimization: run only when an interesting event happens
                               ;; (reading the app from DB is slowish; consider an in-memory event-based solution instead)
                               (= :application.event/submitted (:event/type event))))
                     (map :application/id)
                     distinct)]
    (->> app-ids
         (mapcat #(generate-commands-for-application-id %))
         doall)))
