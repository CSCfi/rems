(ns rems.validate
  "Validating data in the database."
  (:require [clojure.tools.logging :as log]
            [rems.db.core :as db]
            [rems.db.applications :as applications]))

(defn- validate-application
  [id]
  (try
    (applications/get-application-state id)
    (catch Throwable e
      (log/errorf "Application %s failed" id)
      (log/error e)
      false)))

(defn- validate-applications
  []
  (let [applications (db/get-applications {})]
    (log/infof "Validating %s applications" (count applications))
    (every? validate-application (map :id applications))))

(defn validate
  []
  (log/info "Validating data")
  (let [ret (validate-applications)]
    (if ret
      (log/infof "Validations passed")
      (log/errorf "Validations failed"))
    ret))
