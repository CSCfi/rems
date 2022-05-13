(ns rems.application.expirer-bot
  "A bot that sends notification to application members when
   application is about to expire.

   See also: docs/bots.md"
  (:require [clojure.test :refer [deftest testing is]]
            [clj-time.core :as time]
            [rems.config :refer [env]])
  (:import [org.joda.time Period]))

(def bot-userid "expirer-bot")

(defn- get-last-activity [application]
  (let [last-event (last (:application/events application))]
    (if (= (:event/type last-event)
           :application.event/expiration-notifications-sent)
      (:last-activity last-event) ; because events update :application/last-activity timestamp
      (:application/last-activity application))))

(defn- expiration-time [application]
  (when-some [delete-after (get-in env [:application-expiration
                                        (:application/state application)
                                        :delete-after])]
    (-> (get-last-activity application)
        (time/plus (Period/parse delete-after)))))

(defn- expire-application [application]
  (when (some-> (expiration-time application)
                (time/before? (time/now)))
    {:type :application.command/delete
     :time (time/now)
     :actor bot-userid
     :application-id (:application/id application)}))

(deftest test-expire-application
  (with-redefs [env {:application-expiration {:application.state/draft {:delete-after "P90D"}
                                              :application.state/closed {:delete-after "P7D"}}}]
    (testing "should identify expired application by state"
      (let [over-90d-ago (time/minus (time/now) (time/days 90) (time/seconds 1))
            over-7d-ago (time/minus (time/now) (time/days 7) (time/seconds 1))
            over-1d-ago (time/minus (time/now) (time/days 1) (time/seconds 1))]
        (is (some? (expire-application {:application/state :application.state/draft
                                        :application/last-activity over-90d-ago})))
        (is (not (expire-application {:application/state :application.state/draft
                                      :application/last-activity over-7d-ago})))
        (is (some? (expire-application {:application/state :application.state/closed
                                        :application/last-activity over-7d-ago})))
        (is (not (expire-application {:application/state :application.state/closed
                                      :application/last-activity over-1d-ago})))
        (is (not (expire-application {:application/state :application.state/rejected
                                      :application/last-activity over-90d-ago})))))))

(defn- reminder-time [application]
  (let [expiration (get-in env [:application-expiration
                                (:application/state application)])
        delete-after (:delete-after expiration)
        reminder-before (:reminder-before expiration)]
    (when (and delete-after reminder-before)
      (-> (:application/last-activity application)
          (time/plus (Period/parse delete-after))
          (time/minus (Period/parse reminder-before))))))

(deftest test-reminder-time
  (with-redefs [env {:application-expiration {:application.state/draft {:delete-after "P90D"
                                                                        :reminder-before "P7D"}
                                              :application.state/closed {:delete-after "P7D"}
                                              :application.state/rejected {:reminder-before "P7D"}}}]
    (let [now (time/now)]
      (testing "should return reminder time when config is valid"
        (is (time/equal? (time/plus now (time/days 83))
                         (reminder-time {:application/state :application.state/draft
                                         :application/last-activity now})))
        (is (time/equal? now
                         (reminder-time {:application/state :application.state/draft
                                         :application/last-activity (time/minus now (time/days 83))})))
        (is (time/equal? (time/minus now (time/days 1))
                         (reminder-time {:application/state :application.state/draft
                                         :application/last-activity (time/minus now (time/days 84))})))
        (doseq [state [:application.state/closed
                       :application.state/rejected
                       :application.state/submitted]]
          (is (nil? (reminder-time {:application/state state
                                    :application/last-activity now}))
              (str state " returns nil because configuration is missing")))))))

(defn- should-send-email [application]
  (let [last-event-type (-> application :application/events last :event/type)]
    (and (not= last-event-type
               :application.event/expiration-notifications-sent)
         (some-> (reminder-time application)
                 (time/before? (time/now))))))

(defn- send-expiration-notifications [application]
  (when (should-send-email application)
    {:type :application.command/send-expiration-notifications
     :time (time/now)
     :actor bot-userid
     :application-id (:application/id application)
     :last-activity (:application/last-activity application)
     :expires-on (expiration-time application)}))

(defn run-expirer-bot [application]
  (or (expire-application application)
      (send-expiration-notifications application)))

