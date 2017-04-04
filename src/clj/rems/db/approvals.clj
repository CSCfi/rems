(ns rems.db.approvals
  "Query functions for forms and applications."
  (:require [rems.context :as context]
            [rems.db.core :as db]
            [rems.db.catalogue :refer [get-localized-catalogue-item]]
            [rems.util :refer [index-by]]
            [rems.auth.util :refer [throw-unauthorized]]))

(defn get-approvals []
  (doall
   (for [a (db/get-applications {:applicant context/*user* :state "applied"})]
     (assoc a :catalogue-item
            (get-in (get-localized-catalogue-item {:id (:catid a)})
                    [:localizations context/*lang*])))))
