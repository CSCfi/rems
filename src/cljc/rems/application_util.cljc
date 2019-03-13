(ns rems.application-util)

(defn form-fields-editable? [application]
  (or (contains? #{"draft" "returned" "withdrawn"}
                 (:state application))
      (contains? (:possible-commands application)
                 :rems.workflow.dynamic/save-draft)))

(defn draft? [application]
  (contains? #{"draft"}
             (:state application)))

