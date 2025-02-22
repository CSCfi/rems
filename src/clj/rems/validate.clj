(ns rems.validate
  "Validating data in the database."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [rems.api.forms]
            [rems.application.events :as events]
            [rems.common.form :as common-form]
            [rems.config :refer [env]]
            [rems.api.schema]
            [rems.db.catalogue]
            [rems.db.events]
            [rems.db.organizations]
            [rems.ext.duo]
            [rems.service.form]
            [rems.service.licenses]
            [rems.service.resource]
            [schema.core :as s]))

;; TODO: should be part of rems.db.form
(def ^:private validate-form-template
  (s/validator rems.api.schema/FormTemplate))

(defn validate-forms []
  (doseq [template (rems.service.form/get-form-templates {})]
    (validate-form-template template)
    (when-let [errors (common-form/validate-form-template template [])] ;; we don't want errors for missing languages
      (throw (ex-info "Form template validation failed"
                      {:template template
                       :errors errors})))
    (when (and (:enabled template)
               (not (:archived template)))
      (when-let [errors (common-form/validate-form-template template (:languages env))]
        (log/warn "Languages missing from form template" (:form/id template) (pr-str (:form/internal-name template))
                  errors)))))

(defn validate-organizations []
  ;; only warning for now
  ;; NB: do not validate user organizations, they come from the idp
  (let [organizations (->> (rems.db.organizations/get-organizations-raw) (map :organization/id) set)
        valid-organization? (fn [organization] (contains? organizations (:organization/id organization)))]
    (doseq [form (rems.service.form/get-form-templates {})]
      (when-not (valid-organization? (:organization form))
        (log/warn "Unrecognized organization in form:" (pr-str form))))
    (doseq [resource (rems.service.resource/get-resources nil)]
      (when-not (valid-organization? (:organization resource))
        (log/warn "Unrecognized organization in resource:" (pr-str resource))))
    (doseq [license (rems.service.licenses/get-all-licenses {})]
      (when-not (valid-organization? (:organization license))
        (log/warn "Unrecognized organization in license:" (pr-str license))))
    (doseq [item (rems.db.catalogue/get-catalogue-items)]
      (when-not (valid-organization? (:organization item))
        (log/warn "Unrecognized organization in catalogue item:" (pr-str item))))))

(defn validate []
  (log/info "Validating configuration")
  (when-let [old-attributes (some-> env :oidc-userid-attribute list flatten)]
    (throw (ex-info "Please migrate to :oidc-userid-attributes"
                    (doall
                     {:from (select-keys env [:oidc-userid-attribute])
                      :to {:oidc-userid-attributes (vec
                                                    (for [attribute old-attributes]
                                                      {:attribute attribute}))}}))))

  (log/info "Validating data")
  (try
    (validate-forms)
    (events/validate-events (rems.db.events/get-all-events-since 0))
    (validate-organizations)
    (when (:enable-duo env)
      (when (empty? (rems.ext.duo/get-duo-codes))
        (throw (ex-info "No DUO codes though `:enable-duo` is set." {}))))
    (log/info "Validations passed")
    (catch Throwable t
      (log/error t "Validations failed" (with-out-str (when-let [data (ex-data t)]
                                                        (pprint data))))
      t)))
