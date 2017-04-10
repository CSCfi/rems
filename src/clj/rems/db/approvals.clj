(ns rems.db.approvals
  "Query functions for approvals."
  (:require [clojure.tools.logging :as log]
            [conman.core :as conman]
            [rems.env :refer [*db*]]
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

(defn- handle-approved [application round]
  (if (= round (:fnlround application))
    (do (db/update-application-state!
         {:id (:id application) :user (get-user-id)
          :curround round :state "approved"})
        (db/add-entitlement!
         {:application (:id application) :user (get-user-id)
          :resource (:catid application)}))
    (db/update-application-state!
     {:id (:id application) :user (get-user-id)
      :curround (inc round) :state "applied"})))

(defn- handle-rejected [application round]
  (db/update-application-state!
   {:id (:id application) :user (get-user-id)
    :curround round :state "rejected"}))

(defn approve [application-id round state comment]
  (assert (#{:approved :rejected} state))
  (when-not (approver? application-id)
    (throw-unauthorized))
  (let [application (first (db/get-applications {:id application-id}))]
    (assert application (str "Application " application-id " not found!"))
    (assert (= round (:curround application))
            (str "Mismatch: tried to approve round " round " of application "
                 (pr-str application)))
    (when-not (= "applied" (:state application))
      (log/info "Tried to approve application with bad state: %s" (pr-str application))
      (throw-unauthorized))

    (conman/with-transaction [*db* {:isolation :serializable}]
      (db/add-application-approval!
       {:id application-id
        :user (get-user-id)
        :round round
        :comment comment
        :state (name state)})
      (case state
        :approved (handle-approved application round)
        :rejected (handle-rejected application round)))))
