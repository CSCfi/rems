(ns rems.application.expirer-bot
  "A bot that sends notification to application members when
   application is about to expire.

   See also: docs/bots.md"
  (:require [clojure.test :refer [deftest testing is]]
            [clj-time.core :as time]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [getx]]
            [rems.config :refer [env]]
            [rems.permissions :as permissions])
  (:import [org.joda.time Period]))

(def bot-userid "expirer-bot")

;; XXX: could be in utils too
(defn- time-before-or-equal? [this that]
  (or (time/before? this that)
      (time/equal? this that)))

(defn- get-last-event [application]
  (last (:application/events application)))

(defn- get-last-event-when-notification [application]
  (let [last-event (get-last-event application)]
    (when (= (:event/type last-event)
             :application.event/expiration-notifications-sent)
      last-event)))

(defn- calculate-expiration-time [expiration application]
  (when-let [delete-after (:delete-after expiration)]
    (some-> application
            application-util/get-last-applying-user-event ; NB: often :application/last-activity is good too
            (getx :event/time)
            (time/plus (Period/parse delete-after)))))

(defn- get-expiration-time [application]
  (some-> application
          get-last-event-when-notification
          :application/expires-on))

(defn- is-reminder-configured? [expiration]
  (:reminder-before expiration))

(defn- expiration-notification-sent?
  "Has the expiration notification been sent and nothing else has happened since?"
  [application]
  (some? (get-last-event-when-notification application)))

(defn- calculate-reminder-time [expiration application]
  (let [delete-after (:delete-after expiration)
        reminder-before (:reminder-before expiration)]
    (when (and delete-after reminder-before)
      (-> (:event/time (application-util/get-last-applying-user-event application))
          (time/plus (Period/parse delete-after))
          (time/minus (Period/parse reminder-before))))))

(defn- calculate-notification-window
  "Calculate date when enough time can be deemed to have passed since reminding
   user of pending expiration."
  [expiration notification-time]
  (when-let [reminder-before (:reminder-before expiration)]
    (time/plus notification-time (Period/parse reminder-before))))

(defn- enough-time-has-passed-since-notification?
  "Has the user had enough time to react to the reminder?"
  [expiration application now]
  (when-let [notification-time (:event/time (get-last-event-when-notification application))]
    (when-let [notification-window (calculate-notification-window expiration notification-time)]
      (time-before-or-equal? notification-window now))))

(defn- need-to-wait-for-notification? [expiration application now]
  (and (is-reminder-configured? expiration)
       (or (not (expiration-notification-sent? application))
           (not (enough-time-has-passed-since-notification? expiration application now)))))

(defn- expire-application [expiration application now]
  (when-not (need-to-wait-for-notification? expiration application now)
    (when-let [expiration-time (or (get-expiration-time application)
                                   (calculate-expiration-time expiration application))]
      (when (time-before-or-equal? expiration-time now)
        {:type :application.command/delete
         :time now
         :actor bot-userid
         :application-id (:application/id application)
         :expires-on expiration-time}))))

(deftest test-expire-application
  (let [now (time/now)
        exactly-90d-ago (time/minus now (time/days 90))
        exactly-7d-ago (time/minus now (time/days 7))
        exactly-1d-ago (time/minus now (time/days 1))
        created-event (fn [dt]
                        {:event/type :application.event/created
                         :event/time dt
                         :event/actor "alice"})]

    (testing "without reminders"
      (testing "expired draft"
        (is (some? (expire-application {:delete-after "P90D"}
                                       (-> {:application/state :application.state/draft
                                            :application/last-activity exactly-90d-ago
                                            :application/events [(created-event exactly-90d-ago)]}
                                           (permissions/give-role-to-users :applicant #{"alice"}))
                                       now))))

      (testing "only applicant activity is counted towards last activity"
        (is (some? (expire-application {:delete-after "P90D"}
                                       (-> {:application/state :application.state/draft
                                            :application/last-activity exactly-90d-ago
                                            :application/events [(created-event exactly-90d-ago)
                                                                 {:event/type :application.event/remarked
                                                                  :event/time exactly-1d-ago
                                                                  :event/actor "hannah"}]}
                                           (permissions/give-role-to-users :applicant #{"alice"})
                                           (permissions/give-role-to-users :handler #{"hannah"}))
                                       now))))

      (testing "draft expires in 83 days"
        (is (nil? (expire-application {:delete-after "P90D"}
                                      (-> {:application/state :application.state/draft
                                           :application/last-activity exactly-7d-ago
                                           :application/events [(created-event exactly-7d-ago)]}
                                          (permissions/give-role-to-users :applicant #{"alice"}))
                                      now)))))

    (testing "with reminder set"
      (testing "draft expired 2 seconds ago but reminder has not been sent yet"
        (is (nil? (expire-application {:delete-after "P90D"
                                       :reminder-before "P7D"}
                                      (-> {:application/state :application.state/draft
                                           :application/last-activity exactly-90d-ago
                                           :application/events [(created-event exactly-90d-ago)]}
                                          (permissions/give-role-to-users :applicant #{"alice"}))
                                      now))))

      (testing "reminder has been sent 1 day before draft expiration"
        (is (time/equal? (time/plus now (time/days 6))
                         (calculate-notification-window {:delete-after "P90D"
                                                         :reminder-before "P7D"}
                                                        exactly-1d-ago)))
        (is (nil? (expire-application {:delete-after "P90D"
                                       :reminder-before "P7D"}
                                      (-> {:application/state :application.state/draft
                                           :application/last-activity exactly-90d-ago
                                           :application/events [(created-event exactly-90d-ago)
                                                                {:event/type :application.event/expiration-notifications-sent
                                                                 :event/time exactly-1d-ago
                                                                 :application/expires-on (time/plus now (time/days 6))}]}
                                          (permissions/give-role-to-users :applicant #{"alice"}))
                                      now))))

      (testing "reminder has been sent 7 days before draft expiration"
        (is (time/equal? now
                         (calculate-notification-window {:delete-after "P90D"
                                                         :reminder-before "P7D"}
                                                        exactly-7d-ago)))
        (is (some? (expire-application {:delete-after "P90D"
                                        :reminder-before "P7D"}
                                       (-> {:application/state :application.state/draft
                                            :application/last-activity exactly-90d-ago
                                            :application/events [(created-event exactly-90d-ago)
                                                                 {:event/type :application.event/expiration-notifications-sent
                                                                  :event/time exactly-7d-ago
                                                                  :application/expires-on now}]}
                                           (permissions/give-role-to-users :applicant #{"alice"}))
                                       now)))))

    (testing "if not configured"
      (is (nil? (expire-application nil
                                    (-> {:application/state :application.state/draft
                                         :application/last-activity exactly-90d-ago
                                         :application/events [(created-event exactly-90d-ago)]}
                                        (permissions/give-role-to-users :applicant #{"alice"}))
                                    now))))))

(deftest test-calculate-reminder-time
  (let [now (time/now)
        exactly-83d-ago (time/minus now (time/days 83))
        exactly-84d-ago (time/minus now (time/days 84))
        created-event (fn [dt]
                        {:event/type :application.event/created
                         :event/time dt
                         :event/actor "alice"})]
    (testing "when draft expires in 90 days"
      (is (time/equal? (time/plus now (time/days 83))
                       (calculate-reminder-time {:delete-after "P90D"
                                                 :reminder-before "P7D"}
                                                (-> {:application/state :application.state/draft
                                                     :application/last-activity now
                                                     :application/events [(created-event now)]}
                                                    (permissions/give-role-to-users :applicant #{"alice"}))))))

    (testing "when draft expires in 7 days"
      (is (time/equal? now
                       (calculate-reminder-time {:delete-after "P90D"
                                                 :reminder-before "P7D"}
                                                (-> {:application/state :application.state/draft
                                                     :application/last-activity exactly-83d-ago
                                                     :application/events [(created-event exactly-83d-ago)]}
                                                    (permissions/give-role-to-users :applicant #{"alice"}))))))

    (testing "when draft expires in 6 days"
      (is (time/equal? (time/minus now (time/days 1))
                       (calculate-reminder-time {:delete-after "P90D"
                                                 :reminder-before "P7D"}
                                                (-> {:application/state :application.state/draft
                                                     :application/last-activity exactly-84d-ago
                                                     :application/events [(created-event exactly-84d-ago)]}
                                                    (permissions/give-role-to-users :applicant #{"alice"}))))))

    (testing "when reminder is not configured"
      (doseq [expiration [nil
                          {:delete-after "P90D"}
                          {:delete-after "P90D" :reminder-before nil}]]
        (is (nil? (calculate-reminder-time expiration
                                           (-> {:application/state :application.state/draft
                                                :application/last-activity now
                                                :application/events [(created-event now)]}
                                               (permissions/give-role-to-users :applicant #{"alice"})))))))))

(defn- should-send-notification-email? [expiration application now]
  (when-let [reminder-time (calculate-reminder-time expiration application)]
    (and (not (expiration-notification-sent? application))
         (time-before-or-equal? reminder-time now))))

(defn- send-expiration-notifications [expiration application now]
  (when (should-send-notification-email? expiration application now)
    {:type :application.command/send-expiration-notifications
     :time now
     :actor bot-userid
     :application-id (:application/id application)
     :expires-on (calculate-notification-window expiration now)}))

(defn run-expirer-bot [application]
  (let [expiration (get-in env [:application-expiration (:application/state application)])
        now (time/now)]
    (or (expire-application expiration application now)
        (send-expiration-notifications expiration application now))))
