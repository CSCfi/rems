(ns rems.application.eraser
  (:require [better-cond.core :as b]
            [clojure.tools.logging :as log]
            [clj-time.core :as time]
            [mount.core :as mount]
            [rems.application.expirer-bot :as expirer-bot]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.scheduler :as scheduler]
            [rems.service.command :as command]
            [rems.service.users]))

(defn run-commands! [cmds]
  (doseq [cmd cmds]
    (log/info (:type cmd) (select-keys cmd [:application-id :expires-on]))
    (let [result (command/command! cmd)]
      (when (not-empty (:errors result))
        (log/warn "Command validation failed:"
                  (:type cmd)
                  (select-keys cmd [:application-id])
                  (select-keys result [:errors]))))))

(defn process-applications! []
  (log/info :start #'process-applications!)
  (b/cond
    (not (rems.service.users/user-exists? "expirer-bot"))
    (log/warn "Cannot process applications, because user expirer-bot does not exist")

    :let [cmds (->> (applications/get-all-unrestricted-applications)
                    (keep expirer-bot/run-expirer-bot))]

    (empty? cmds)
    (log/info "No applications to process")

    :let [process-limit (or (:application-expiration-process-limit env) 50)]
    :do (log/infof "Total of %s applications due for processing (%s expired, %s to send expiration notifications for). Processing is limited to %s applications at a time."
                   (count cmds)
                   (count (->> cmds (filter #(= :application.command/delete (:type %)))))
                   (count (->> cmds (filter #(= :application.command/send-expiration-notifications (:type %)))))
                   process-limit)

    (run-commands! (take process-limit cmds)))
  (log/info :finish #'process-applications!))

(mount/defstate expired-application-poller
  :start (when (:application-expiration env)
           (scheduler/start! "expired-application-poller"
                             process-applications!
                             (.toStandardDuration (time/hours 1))
                             (select-keys env [:buzy-hours])))
  :stop (when expired-application-poller
          (scheduler/stop! expired-application-poller)))

(comment
  (mount/defstate expired-application-poller-test
    :start (scheduler/start! "expired-application-poller-test"
                             (fn [] (with-redefs [env (assoc env
                                                             :application-expiration {:application.state/draft {:delete-after "P1D"
                                                                                                                :reminder-before "P1D"}}
                                                             :application-expiration-process-limit 1)]
                                      (process-applications!)))
                             (.toStandardDuration (time/seconds 10)))
    :stop (scheduler/stop! expired-application-poller-test))

  (mount/start #{#'expired-application-poller-test})
  (mount/stop #{#'expired-application-poller-test})

  (with-redefs [env (assoc env
                           :application-expiration {:application.state/draft {:delete-after "P90D"
                                                                              :reminder-before "P7D"}}
                           :application-expiration-process-limit 1)]
    (process-applications!)))

