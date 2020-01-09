(ns rems.validate
  "Validating data in the database."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [rems.api.forms]
            [rems.application.events :as events]
            [rems.db.events :as events-db]
            [rems.db.form :as form]
            [schema.core :as s]))

(def ^:private validate-form-template
  (s/validator rems.api.schema/FormTemplate))

(defn validate-forms []
  (doseq [template (form/get-form-templates {})]
    (validate-form-template template)))

(defn validate []
  (log/info "Validating data")
  (try
    (validate-forms)
    (events/validate-events (events-db/get-all-events-since 0))
    (log/info "Validations passed")
    (catch Throwable t
      (log/error t "Validations failed" (with-out-str (when-let [data (ex-data t)]
                                                        (pprint data))))
      t)))
