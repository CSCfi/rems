(ns rems.validate
  "Validating data in the database."
  (:require [clojure.pprint :as pprint]
            [clojure.tools.logging :as log]
            [rems.api.applications-v2 :as applications-v2]))

(defn validate []
  (log/info "Validating data")
  (try
    ;; will throw an exception if there are non-valid events
    (applications-v2/get-all-unrestricted-applications)
    (log/info "Validations passed")
    (catch Throwable e
      (log/error "Validations failed"
                 (when-let [data (ex-data e)]
                   (with-out-str
                     (println)
                     (pprint/pprint data))))
      (log/error e)
      e)))
