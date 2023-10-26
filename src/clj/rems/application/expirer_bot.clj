(ns rems.application.expirer-bot
  "A bot that sends notification to application members when
   application is about to expire.

   See also: docs/bots.md"
  (:require [clojure.test :refer [deftest testing is]]
            [clj-time.core :as time]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [getx]]
            [rems.config :refer [env]])
  (:import [org.joda.time Period]))

(def bot-userid "expirer-bot")

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
  "Has the expiration notification been sent and nothing else has happened since."
  [expiration application]
  (some? (get-last-event-when-notification application)))

(defn- calculate-reminder-time [expiration application]
  (let [delete-after (:delete-after expiration)
        reminder-before (:reminder-before expiration)]
    (when (and delete-after reminder-before)
      (-> (:event/time (application-util/get-last-applying-user-event application))
          (time/plus (Period/parse delete-after))
          (time/minus (Period/parse reminder-before))))))

(defn- enough-time-has-passed-since-notification?
  "Has the user had enough time to react to the reminder?"
  [expiration application now]
  (when-let [notification-time (:event/time (get-last-event-when-notification application))]
    (when-let [reminder-before (:reminder-before expiration)]
      (time/before? (time/plus notification-time (Period/parse reminder-before))
                    now))))

(defn- need-to-wait-for-notification? [expiration application now]
  (and (is-reminder-configured? expiration)
       (or (not (expiration-notification-sent? expiration application))
           (not (enough-time-has-passed-since-notification? expiration application now)))))

(defn- expire-application [expiration application now]
  (when-not (need-to-wait-for-notification? expiration application now)
    (when-let [expiration-time (or (get-expiration-time application)
                                   (calculate-expiration-time expiration application))]
      (when (time/before? expiration-time now)
        {:type :application.command/delete
         :time (time/now)
         :actor bot-userid
         :application-id (:application/id application)}))))

(deftest test-expire-application
  (let [now (time/now)
        over-90d-ago (time/minus now (time/days 90) (time/seconds 2))
        over-7d-ago (time/minus now (time/days 7) (time/seconds 2))
        over-1d-ago (time/minus now (time/days 1) (time/seconds 2))]
    (testing "without reminders"
      (is (some? (expire-application {:delete-after "P90D"}
                                     {:application/applicant {:userid "alice"}
                                      :application/state :application.state/draft
                                      :application/last-activity over-90d-ago
                                      :application/events [{:event/type :application.event/created
                                                            :event/time over-90d-ago
                                                            :event/actor "alice"}
                                                           ;; skip because not applying user
                                                           {:event/type :application.event/remarked
                                                            :event/time over-1d-ago
                                                            :event/actor "hannah"}]}
                                     now)))
      (is (nil? (expire-application {:delete-after "P90D"}
                                    {:application/state :application.state/draft
                                     :application/last-activity over-7d-ago}
                                    now))))

    (testing "with reminder set"
      (is (nil? (expire-application {:delete-after "P90D"
                                     :reminder-before "P7D"}
                                    {:application/state :application.state/draft
                                     :application/last-activity over-90d-ago}
                                    now))
          "reminder not yet sent")

      (is (nil? (expire-application {:delete-after "P90D"
                                     :reminder-before "P7D"}
                                    {:application/state :application.state/draft
                                     :application/last-activity over-90d-ago
                                     :application/events [{:type :application.command/send-expiration-notifications
                                                           :time over-1d-ago
                                                           :expires-on now}]}
                                    now))
          "reminder sent but time not passed")
      (is (some? (expire-application {:delete-after "P90D"
                                      :reminder-before "P7D"}
                                     {:application/state :application.state/draft
                                      :application/last-activity over-90d-ago
                                      :application/events [{:event/type :application.event/expiration-notifications-sent
                                                            :event/time over-7d-ago
                                                            :application/expires-on now}]}
                                     (time/plus now (time/seconds 1))))
          "reminder sent and enough time passed"))

    (testing "if not configured"
      (is (nil? (expire-application nil
                                    {:application/state :application.state/draft
                                     :application/last-activity over-90d-ago}
                                    now))))))

(deftest test-calculate-reminder-time
  (let [now (time/now)]
    (testing "should return reminder time when config is valid"
      (is (time/equal? (time/plus now (time/days 83))
                       (calculate-reminder-time {:delete-after "P90D"
                                                 :reminder-before "P7D"}
                                                {:application/applicant {:userid "alice"}
                                                 :application/state :application.state/draft
                                                 :application/last-activity now
                                                 :application/events [{:event/type :application.event/created
                                                                       :event/time now
                                                                       :event/actor "alice"}]})))
      (is (time/equal? now
                       (calculate-reminder-time {:delete-after "P90D"
                                                 :reminder-before "P7D"}
                                                {:application/applicant {:userid "alice"}
                                                 :application/state :application.state/draft
                                                 :application/last-activity (time/minus now (time/days 83))
                                                 :application/events [{:event/type :application.event/created
                                                                       :event/time (time/minus now (time/days 83))
                                                                       :event/actor "alice"}]})))
      (is (time/equal? (time/minus now (time/days 1))
                       (calculate-reminder-time {:delete-after "P90D"
                                                 :reminder-before "P7D"}
                                                {:application/applicant {:userid "alice"}
                                                 :application/state :application.state/draft
                                                 :application/last-activity (time/minus now (time/days 84))
                                                 :application/events [{:event/type :application.event/created
                                                                       :event/time (time/minus now (time/days 84))
                                                                       :event/actor "alice"}]})))
      (is (nil? (calculate-reminder-time nil
                                         {:application/applicant {:userid "alice"}
                                          :application/state :application.state/draft
                                          :application/last-activity now
                                          :application/events [{:event/type :application.event/created
                                                                :event/time now}]}))
          "returns nil because configuration is missing")

      (is (nil? (calculate-reminder-time {:delete-after "P90D"}
                                         {:application/applicant {:userid "alice"}
                                          :application/state :application.state/draft
                                          :application/last-activity now
                                          :application/events [{:event/type :application.event/created
                                                                :event/time now}]}))
          "returns nil because configuration is missing")

      (is (nil? (calculate-reminder-time {:delete-after "P90D"
                                          :reminder-before nil}
                                         {:application/applicant {:userid "alice"}
                                          :application/state :application.state/draft
                                          :application/last-activity now
                                          :application/events [{:event/type :application.event/created
                                                                :event/time now}]}))
          "returns nil because configuration is missing"))))

(defn- should-send-notification-email? [expiration application now]
  (when-let [reminder-time (calculate-reminder-time expiration application)]
    (and (not (expiration-notification-sent? expiration application))
         (time/before? reminder-time now))))

(defn- send-expiration-notifications [expiration application now]
  (when (should-send-notification-email? expiration application now)
    {:type :application.command/send-expiration-notifications
     :time (time/now)
     :actor bot-userid
     :application-id (:application/id application)
     :expires-on (calculate-expiration-time expiration application)}))

(defn run-expirer-bot [application]
  (let [expiration (get-in env [:application-expiration (:application/state application)])
        now (time/now)]
    (or (expire-application expiration application now)
        (send-expiration-notifications expiration application now))))
