(ns rems.db.application)

(defn draft? [state]
  #(contains? #{"draft" "returned" "withdrawn"
                :rems.workflow.dynamic/draft :rems.workflow.dynamic/returned
                ;; TODO add dynamic withdrawn state
                }
              state))
