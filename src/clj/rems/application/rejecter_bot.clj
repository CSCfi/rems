(ns rems.application.rejecter-bot
  (:require [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [rems.common.application-util :as application-util]
            [rems.permissions :as permissions]
            [rems.service.application :as application]))

(defn- should-reject? [application]
  (not (empty? (:application/blacklist application))))

(defn- can-reject? [application]
  (contains? (permissions/user-permissions application "rejecter-bot") :application.command/reject))

(defn- consider-rejecting [application]
  (when (and (application-util/is-handler? application "rejecter-bot")
             (should-reject? application)
             (can-reject? application))
    (log/info "Rejecter bot rejecting application" (:application/id application) "based on blacklist" (:application/blacklist application))
    [{:type :application.command/reject
      :application-id (:application/id application)
      :time (time/now)
      :comment ""
      :actor "rejecter-bot"}]))

(defn reject-all-applications-by
  "Go through all applications by the given user-ids and reject any if necessary. Returns sequence of commands."
  [& user-ids]
  (->> (mapcat application/get-full-personalized-applications-by-user user-ids)
       (mapv :application/id)
       distinct
       (mapv application/get-full-internal-application)
       (mapcat consider-rejecting)))

(defn run-rejecter-bot [new-events]
  (let [by-type (group-by :event/type new-events)
        submissions (get by-type :application.event/submitted)
        submitted-applications (mapv #(application/get-full-internal-application (:application/id %)) submissions)
        revokes (get by-type :application.event/revoked)
        revoked-users (->> revokes
                           (map (comp application/get-full-internal-application :application/id))
                           (mapcat application-util/applicant-and-members)
                           (map :userid))]
    (doall
     (concat
      (mapcat consider-rejecting submitted-applications)
      (apply reject-all-applications-by revoked-users)))))
