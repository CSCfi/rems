(ns rems.application.rejecter-bot
  (:require [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [distinct-by]]
            [rems.db.applications :as applications]
            [rems.permissions :as permissions]))

(def bot-userid "rejecter-bot")

(defn- should-reject? [application]
  (not (empty? (:application/blacklist application))))

(defn- can-reject? [application]
  (contains? (permissions/user-permissions application bot-userid) :application.command/reject))

(defn- consider-rejecting [application]
  (when (and (application-util/is-handler? application bot-userid)
             (should-reject? application)
             (can-reject? application))
    (log/info "Rejecter bot rejecting application" (:application/id application) "based on blacklist" (:application/blacklist application))
    [{:type :application.command/reject
      :application-id (:application/id application)
      :time (time/now)
      :comment ""
      :actor bot-userid}]))

(defn- generate-commands [event application]
  (when (= :application.event/submitted (:event/type event)) ;; rejecter bot only reacts to fresh applications
    (consider-rejecting application)))

(defn reject-all-applications-by
  "Go through all applications by the given user-id and reject any if necessary. Returns sequence of commands."
  [user-id]
  (let [apps (mapv #(applications/get-application (:application/id %))
                   (applications/get-my-applications user-id))]
    (mapcat consider-rejecting apps)))

(defn run-rejecter-bot [new-events]
  (let [by-type (group-by :event/type new-events)
        submissions (get by-type :application.event/submitted)
        submitted-applications (mapv #(applications/get-application (:application/id %)) submissions)
        revokes (get by-type :application.event/revoked)
        revoked-users (distinct (for [event revokes
                                      member (application-util/applicant-and-members (applications/get-application (:application/id event)))]
                                  (:userid member)))]
    (doall
     (concat
      (mapcat consider-rejecting submitted-applications)
      ;; TODO hacky removal of duplicate rejects
      (distinct-by :application-id (mapcat reject-all-applications-by revoked-users))))))
