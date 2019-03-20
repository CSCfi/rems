(ns rems.application-util)

(defn form-fields-editable? [application]
  (or (contains? #{"draft" "returned" "withdrawn"} ;; TODO: remove v1 api usage
                 (:state application))
      (contains? (or (:possible-commands application) ;; TODO: remove v1 api usage
                     (:application/permissions application))
                 :rems.workflow.dynamic/save-draft)))
