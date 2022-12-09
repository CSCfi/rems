(ns rems.application.eraser
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as time]
            [mount.core :as mount]
            [rems.service.command :as command]
            [rems.application.expirer-bot :as expirer-bot]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.users :as users]
            [rems.scheduler :as scheduler]))

(defn process-applications! []
  (log/info :start #'process-applications!)
  ; check that bot user exists, else log missing
  (if (users/user-exists? expirer-bot/bot-userid)
    (doseq [application (applications/get-all-unrestricted-applications)]
      (when-some [cmd (expirer-bot/run-expirer-bot application)]
        (command/command! cmd)))
    (log/warnf "Cannot process applications, because user %s does not exist"
               expirer-bot/bot-userid))
  (applications/reload-cache!)
  (log/info :finish #'process-applications!))

(mount/defstate expired-application-poller
  :start (when (:application-expiration env)
           (scheduler/start! "expired-application-poller"
                             process-applications!
                             (.toStandardDuration (time/days 1))))
  :stop (when expired-application-poller
          (scheduler/stop! expired-application-poller)))

(comment
  (let [config {:application.state/draft {:delete-after "P1D"
                                          :reminder-before "P1D"}}]
    (mount/defstate expired-application-poller-test
      :start (scheduler/start! "expired-application-poller-test"
                               (fn [] (with-redefs [env (assoc env
                                                               :application-expiration
                                                               config)]
                                        (process-applications!)))
                               (.toStandardDuration (time/seconds 10)))
      :stop (scheduler/stop! expired-application-poller-test)))

  (mount/start #{#'expired-application-poller-test})
  (mount/stop #{#'expired-application-poller-test}))

