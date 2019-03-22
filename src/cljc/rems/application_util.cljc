(ns rems.application-util)

(defn form-fields-editable? [application]
  (contains? (or (:possible-commands application) ;; TODO: remove v1 api usage
                 (:application/permissions application))
             :rems.workflow.dynamic/save-draft))

(defn in-processing? [application]
  (not (contains? #{:rems.workflow.dynamic/approved
                    :rems.workflow.dynamic/rejected
                    :rems.workflow.dynamic/closed}
                  (get-in application [:application/workflow :workflow.dynamic/state]))))