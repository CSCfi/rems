(ns rems.validate
  "Validating data in the database."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [rems.api.applications-v2 :as applications-v2]))

(defn validate []
  (log/info "Validating data")
  (try
    ;; will throw an exception if there are non-valid events
    (applications-v2/get-all-unrestricted-applications)
    (log/info "Validations passed")
    (catch Throwable t
      (log/error t "Validations failed" (with-out-str (when-let [data (ex-data t)]
                                                        (pprint data))))
      t)))
