(ns rems.application.process-managers
  "Miscellaneous process managers.

  NB: An event manager should return an empty sequence (or `nil`) if it doesn't create new events itself."
  (:require [clojure.set :refer [difference]]
            [rems.application.expirer-bot :as expirer-bot]
            [rems.common.application-util :as application-util]
            [rems.db.applications]
            [rems.db.attachments]
            [rems.service.attachment]
            [rems.service.blacklist]))

(defn revokes-to-blacklist
  "Revokation causes the users to be blacklisted."
  [new-events]
  (doseq [event new-events
          :when (= :application.event/revoked (:event/type event))
          :let [application (rems.db.applications/get-application-internal (:application/id event))]
          user (application-util/applicant-and-members application)
          resource (:application/resources application)]
    (rems.service.blacklist/add-user-to-blacklist! (:event/actor event)
                                                   {:blacklist/user {:userid (:userid user)}
                                                    :blacklist/resource {:resource/ext-id (:resource/ext-id resource)}
                                                    :comment (:application/comment event)})))

(defn delete-applications
  "The deleted event causes a side-effect that completely deletes the application."
  [new-events]
  (doseq [event new-events]
    (when (= :application.event/deleted (:event/type event))
      (rems.db.applications/delete-application! (:application/id event)))))

(defn delete-orphan-attachments [application-id]
  (let [application (rems.db.applications/get-application-internal application-id)
        attachments-in-use (set (rems.service.attachment/get-attachments-in-use application))
        all-attachments (set (map :attachment/id (:application/attachments application)))]
    (doseq [attachment-id (difference all-attachments attachments-in-use)]
      (rems.db.attachments/delete-attachment! attachment-id))))

(defn delete-orphan-attachments-on-submit
  "When an application is submitted, we delete its unused attachments, if any."
  [new-events]
  (doseq [event new-events]
    (when (= :application.event/submitted (:event/type event))
      (delete-orphan-attachments (:application/id event)))))

(defn clear-redacted-attachments
  "`:application.event/attachments-redacted` causes a side-effect that clears
   contents of the redacted attachments in database."
  [new-events]
  (doseq [event new-events
          :when (= :application.event/attachments-redacted (:event/type event))
          attachment (:event/redacted-attachments event)]
    (rems.db.attachments/redact-attachment! (:attachment/id attachment))))
