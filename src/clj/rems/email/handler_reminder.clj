(ns rems.email.handler-reminder
  (:require [rems.api.services.workflow :as workflow]))

(defn get-handlers []
  (let [workflows (workflow/get-workflows {:enabled true
                                           :archived false})
        handlers (mapcat (fn [wf]
                           (get-in wf [:workflow :handlers]))
                         workflows)]
    (-> (map :userid handlers) distinct sort)))
