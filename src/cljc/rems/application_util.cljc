(ns rems.application-util)

(defn form-fields-editable? [application]
  (or (contains? #{"draft" "returned" "withdrawn"}
                 (:state application))
      (contains? (:possible-commands application)
                 :rems.workflow.dynamic/save-draft)))

(defn draft? [application]
  (contains? #{"draft" "returned" "withdrawn" :rems.workflow.dynamic/draft}
             (:state application)))

(defn is-applicant? [application]
  (:is-applicant? application))

(defn in-processing? [application]
  (not (contains? #{"approved"
                    "rejected"
                    "closed"
                    :rems.workflow.dynamic/approved
                    :rems.workflow.dynamic/rejected
                    :rems.workflow.dynamic/closed}
                  (:state application))))