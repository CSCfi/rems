(ns rems.application-util)

(defn form-fields-editable? [application]
  (or (contains? #{"draft" "returned" "withdrawn"}
                 (:state application))
      (contains? (:possible-commands application)
                 :rems.workflow.dynamic/save-draft)))

(defn draft? [application]
  (or (contains? #{"draft" "returned" "withdrawn"}
                 (:state application))
      (= (:state application) :rems.workflow.dynamic/draft)))

(defn is-applicant? [application]
  (:is-applicant? application))