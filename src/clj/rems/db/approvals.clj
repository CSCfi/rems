(ns rems.db.approvals
  "Query functions for approvals."
  (:require [clojure.tools.logging :as log]
            [rems.context :as context]
            [rems.db.core :as db]
            [rems.db.catalogue :refer [get-localized-catalogue-item]]
            [rems.util :refer [index-by get-user-id]]
            [rems.auth.util :refer [throw-unauthorized]]))

(defn get-approvals []
  (doall
   (for [a (db/get-applications {:approver (get-user-id) :state "applied"})]
     (assoc a :catalogue-item
            (get-in (get-localized-catalogue-item {:id (:catid a)})
                    [:localizations context/*lang*])))))

(defn approver? [application-id]
  (not (empty? (db/get-applications {:id application-id
                                     :approver (get-user-id)
                                     :state "applied"}))))

;; TODO round should be explicit to avoid race conditions
(defn approve [application-id comment]
  (when-not (approver? application-id)
    (throw-unauthorized))
  (let [application (first (db/get-applications {:id application-id}))]
    (assert application (str "Application " application-id " not found!"))
    (when-not (= "applied" (:state application))
      (log/info "Tried to approve application with bad state: %s" (pr-str application))
      (throw-unauthorized))
    (db/add-application-approval!
     {:id application-id
      :user (get-user-id)
      :comment comment
      :state "approved"})
    (db/update-application-state!
     {:id application-id
      :user (get-user-id)
      :curround (inc (:curround application))
      :state "applied"})))
