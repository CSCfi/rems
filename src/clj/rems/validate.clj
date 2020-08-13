(ns rems.validate
  "Validating data in the database."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [rems.api.forms]
            [rems.application.events :as events]
            [rems.common.form :as common-form]
            [rems.config :refer [env]]
            [rems.api.schema]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.services.form :as form]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.resource :as resources]
            [rems.db.events :as events-db]
            [rems.db.organizations :as organizations]
            [schema.core :as s]))

(def ^:private validate-form-template
  (s/validator rems.api.schema/FormTemplate))

(defn validate-forms []
  (doseq [template (form/get-form-templates {})]
    (validate-form-template template)
    (when-let [errors (common-form/validate-form-template template [])] ;; we don't want errors for missing languages
      (throw (ex-info "Form template validation failed"
                      {:template template
                       :errors errors})))
    (when (and (:enabled template)
               (not (:archived template)))
      (when-let [errors (common-form/validate-form-template template (:languages env))]
        (log/warn "Languages missing from form template" (:form/id template) (pr-str (:form/title template))
                  errors)))))

(defn validate-organizations []
  ;; only warning for now
  ;; NB: do not validate user organizations, they come from the idp
  (let [organizations (->> (organizations/get-organizations-raw) (map :organization/id) set)
        valid-organization? (fn [organization] (contains? organizations (:organization/id organization)))]
    (doseq [form (form/get-form-templates {})]
      (when-not (valid-organization? (:organization form))
        (log/warn "Unrecognized organization in form:" (pr-str form))))
    (doseq [resource (resources/get-resources nil)]
      (when-not (valid-organization? (:organization resource))
        (log/warn "Unrecognized organization in resource:" (pr-str resource))))
    (doseq [license (licenses/get-all-licenses {})]
      (when-not (valid-organization? (:organization license))
        (log/warn "Unrecognized organization in license:" (pr-str license))))
    (doseq [item (catalogue/get-localized-catalogue-items)]
      (when-not (valid-organization? (:organization item))
        (log/warn "Unrecognized organization in catalogue item:" (pr-str item))))))

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
