(ns rems.common.application-util
  (:require [rems.common.util :refer [build-index]]))

(defn accepted-licenses? [application userid]
  (let [application-licenses (map :license/id (:application/licenses application))
        user-accepted-licenses (get (:application/accepted-licenses application) userid)]
    (cond (empty? application-licenses) true
          (empty? user-accepted-licenses) false
          :else (every? (set user-accepted-licenses) application-licenses))))

(defn form-fields-editable? [application]
  (contains? (:application/permissions application)
             :application.command/save-draft))

(defn get-member-name [attributes]
  (or (:name attributes)
      (:userid attributes)))

(defn get-applicant-name [application]
  (get-member-name (:application/applicant application)))

(defn applicant-and-members [application]
  (cons (:application/applicant application)
        (:application/members application)))

(defn workflow-handlers [application]
  (set (mapv :userid (get-in application [:application/workflow :workflow.dynamic/handlers]))))

(defn is-handler? [application user]
  (contains? (workflow-handlers application) user))

(defn copy-field-values [application]
  (->> (for [form (:application/forms application)
             field (:form/fields form)]
         {:form/id (:form/id form)
          :field/id (:field/id field)
          :field/value (:field/value field)})
       (build-index [:form/id :field/id] :field/value)))
