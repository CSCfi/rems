(ns rems.validate
  "Validating data in the database."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [rems.api.forms]
            [rems.db.applications :as applications]
            [rems.db.form :as form]
            [schema.core :as s]))

(defn validate-forms []
  (doseq [template (form/get-form-templates {})]
    (s/validate rems.api.schema/FullForm template)))

(defn validate []
  (log/info "Validating data")
  (try
    (validate-forms)
    ;; will throw an exception if there are non-valid events
    (applications/get-all-unrestricted-applications)
    (log/info "Validations passed")
    (catch Throwable t
      (log/error t "Validations failed" (with-out-str (when-let [data (ex-data t)]
                                                        (pprint data))))
      t)))
