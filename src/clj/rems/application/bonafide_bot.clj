(ns rems.application.bonafide-bot
  "A bot that enables workflows where a user can ask another user to vouch
   for their bona fide researcher status."
  (:require [clj-time.core :as time]
            [rems.common.application-util :as application-util]
            [rems.db.applications :as applications]
            [rems.db.users :as users]))

(def bot-userid "bonafide-bot")

(defn- find-email-address [application]
  (some (fn [field]
          (when (= :email (:field/type field))
            (:field/value field)))
        (mapcat :form/fields
                (:application/forms application))))

(defn- may-give-bonafide-status? [user-attributes]
  (contains? #{"so" "system"} (:researcher-status-by user-attributes)))

(defn- generate-commands [event application]
  (when (application-util/is-handler? application bot-userid)
    (case (:event/type event)
      :application.event/submitted
      (let [email (find-email-address application)]
        (assert email (pr-str application))
        [{:type :application.command/invite-decider
          :time (time/now)
          :application-id (:application/id application)
          :actor bot-userid
          :decider {:name "Referer"
                    :email email}}])
      :application.event/decided
      (when (may-give-bonafide-status? (users/get-user (:event/actor event)))
        [{:type (case (:application/decision event)
                  :approved :application.command/approve
                  :rejected :application.command/reject)
          :time (time/now)
          :application-id (:application/id application)
          :actor bot-userid}])

      [])))

(defn run-bonafide-bot [new-events]
  (doall (mapcat #(generate-commands % (applications/get-application (:application/id %)))
                 new-events)))
