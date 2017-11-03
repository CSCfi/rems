(ns rems.tasks
  "Scheduled task definitions."
  (:require [rems.db.applications :as applications]))

(def minutely-schedule "0 * * * * * *")
(def hourly-schedule "0 0 * * * * *")

(def review-timeout-task
  "Task for checking and timing out reviews."
  {:handler (fn [t] (applications/check-review-timeout t))
   :schedule hourly-schedule})

(def standalone
  "Tasks run when in standalone mode"
  {:review-timeout-task review-timeout-task})
