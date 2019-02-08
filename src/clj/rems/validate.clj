(ns rems.validate
  "Validating data in the database."
  (:require [clojure.tools.logging :as log]
            [rems.db.applications :as applications]
            [rems.db.core :as db]))

(defn- validate-application [id]
  (try
    (applications/get-application-state id)
    nil
    (catch Throwable e
      (log/errorf "Application %s failed" id)
      (log/error e)
      [{:invalid-application id}])))

(defn- validate-applications []
  (let [applications (db/get-applications {})]
    (log/infof "Validating %s applications" (count applications))
    (mapcat validate-application (map :id applications))))

(defn validate []
  (log/info "Validating data")
  (let [application-errors (validate-applications)]
    (if (empty? application-errors)
      (log/infof "Validations passed")
      (log/errorf "Validations failed"))
    application-errors))
