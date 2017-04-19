(ns rems.db.approvals
  "Query functions for approvals."
  (:require [clojure.tools.logging :as log]
            [conman.core :as conman]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.catalogue :refer [get-localized-catalogue-item]]
            [rems.db.core :as db]
            [rems.env :refer [*db*]]
            [rems.util :refer [get-user-id]]))

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

(defn- get-round-approval-state
  "Returns :approved, :rejected or :pending."
  [application-id round]
  (let [approvers (db/get-workflow-approvers {:application application-id
                                              :round round})
        approvals (->> {:application application-id
                        :round round}
                       db/get-application-approvals
                       (map :state)
                       set)]
    (cond
      (empty? approvers) ;; no approvals required
      :approved

      (empty? approvals)
      :pending

      (= #{"approved"} approvals)
      :approved

      (= #{"rejected"} approvals)
      :rejected

      :else
      (assert false (format "Don't know what to do with approval set %s for application %s"
                            approvals application-id)))))

(defn- process-approved [application round]
  (log/infof "Application %s approved for round %s" (:id application) round)
  (if (= round (:fnlround application))
    (do (log/infof "Application %s approved" (:id application))
        (db/update-application-state!
         {:id (:id application) :user (get-user-id)
          :curround round :state "approved"})
        (db/add-entitlement!
         {:application (:id application) :user (get-user-id)
          :resource (:catid application)}))
    (db/update-application-state!
     {:id (:id application) :user (get-user-id)
      :curround (inc round) :state "applied"})))

(defn- process-rejected [application round]
  (log/infof "Application %s rejected on round %s" (:id application) round)
  (db/update-application-state!
   {:id (:id application) :user (get-user-id)
    :curround round :state "rejected"}))

(defn process-application
  "Take the application to the next round (and the next, and so on) if necessary
   approvals are in place.

   Also handles updating the application status (applied ->
   approved/rejected) and creating the entitlements for approved
   applications."
  [application-id]
  (let [application (first (db/get-applications {:id application-id}))
        round (:curround application)]
    (assert application (str "Application " application-id " not found!"))
    (if-not (= (:state application) "applied")
      (log/infof "Application %s in state %s: does not need processing"
                application-id (:state application))
      (case (get-round-approval-state application-id round)
        :approved (do (process-approved application round)
                      (recur application-id))
        :rejected (do (process-rejected application round)
                      (recur application-id))
        :pending (log/infof "Application %s pending on round %s" application-id round)))))

(defn- handle [application-id round state comment]
  (assert (#{:approved :rejected} state))
  (when-not (approver? application-id)
    (throw-unauthorized))
  (let [application (first (db/get-applications {:id application-id}))]
    (assert application (str "Application " application-id " not found!"))
    (assert (= round (:curround application))
            (str "Mismatch: tried to approve round " round " of application "
                 (pr-str application)))
    (when-not (= "applied" (:state application))
      (log/infof "Tried to approve application with bad state: %s" (pr-str application))
      (throw-unauthorized))

    (conman/with-transaction [*db* {:isolation :serializable}]
      (db/add-application-approval!
       {:id application-id
        :user (get-user-id)
        :round round
        :comment comment
        :state (name state)})
      (process-application application-id))))

(defn approve [application-id round comment]
  (handle application-id round :approved comment))

(defn reject [application-id round comment]
  (handle application-id round :rejected comment))
