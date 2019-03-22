(ns rems.application-util)

(defn form-fields-editable? [application]
  (contains? (or (:possible-commands application) ;; TODO: remove v1 api usage
                 (:application/permissions application))
             :rems.workflow.dynamic/save-draft))
