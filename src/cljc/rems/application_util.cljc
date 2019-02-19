(ns rems.application-util)

(defn editable? [application]
  (or (contains? #{"draft" "returned" "withdrawn"}
                 (:state application))
      (contains? (:possible-commands application)
                 :rems.workflow.dynamic/save-draft)))
