(ns rems.application.bona-fide-bot
  "A bot that enables workflows where a user can ask another user to vouch
   for their bona fide researcher status.

   See also: docs/bots.md, docs/ga4gh-visas.md"
  (:require [clj-time.core :as time]
            [rems.common.application-util :as application-util]
            [rems.service.application :as application]
            [rems.db.users :as users]))

(defn- find-email-address [application]
  (some (fn [field]
          (when (= :email (:field/type field))
            (:field/value field)))
        (mapcat :form/fields
                (:application/forms application))))

(defn- may-give-bona-fide-status? [user-attributes]
  (contains? #{"so" "system"} (:researcher-status-by user-attributes)))

(defn- generate-commands [event actor-attributes application]
  (when (application-util/is-handler? application "bona-fide-bot")
    (case (:event/type event)
      :application.event/submitted
      (let [email (find-email-address application)]
        (assert email (pr-str application))
        [{:type :application.command/invite-decider
          :time (time/now)
          :application-id (:application/id event)
          :actor "bona-fide-bot"
          :decider {:name "Referer"
                    :email email}}])
      :application.event/decided
      [{:type (if (and (may-give-bona-fide-status? actor-attributes)
                       (= :approved (:application/decision event)))
                :application.command/approve
                :application.command/reject)
        :time (time/now)
        :application-id (:application/id event)
        :actor "bona-fide-bot"}]

      [])))

(defn run-bona-fide-bot [new-events]
  (doall (mapcat #(generate-commands %
                                     (users/get-user (:event/actor %))
                                     (application/get-full-internal-application (:application/id %)))
                 new-events)))
