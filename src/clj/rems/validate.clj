(ns rems.validate
  "Validating data in the database."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [rems.api.forms]
            [rems.application.events :as events]
            [rems.common.form :as common-form]
            [rems.config :refer [env]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.events :as events-db]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [schema.core :as s]))

(def ^:private validate-form-template
  (s/validator rems.api.schema/FormTemplate))

(defn validate-forms []
  (doseq [template (form/get-form-templates {})]
    (validate-form-template template)
    (assert (nil? (common-form/validate-form-template template (:languages env))))))

(defn- valid-organization? [org]
  (contains? (set (:organizations env)) org))

(defn validate-organizations []
  ;; only warning for now
  (doseq [form (form/get-form-templates {})]
    (when-not (valid-organization? (:form/organization form))
      (log/warn "Unrecognized organization in form: " (pr-str form))))
  ;; rems.db.resource/get-resources requires a user to be set (for forbidden-organization?)
  (doseq [resource (db/get-resources {})]
    (when-not (valid-organization? (:organization resource))
      (log/warn "Unrecognized organization in resource: " (pr-str resource))))
  (doseq [license (licenses/get-all-licenses {})]
    (when-not (valid-organization? (:organization license))
      (log/warn "Unrecognized organization in license: " (pr-str license))))
  (doseq [item (catalogue/get-localized-catalogue-items)]
    (when-not (valid-organization? (:organization item))
      (log/warn "Unrecognized organization in catalogue item: " (pr-str item)))))

(defn validate []
  (log/info "Validating data")
  (try
    (validate-forms)
    (events/validate-events (events-db/get-all-events-since 0))
    (validate-organizations)
    (log/info "Validations passed")
    (catch Throwable t
      (log/error t "Validations failed" (with-out-str (when-let [data (ex-data t)]
                                                        (pprint data))))
      t)))
