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
      (:last-activity last-event)
      (:application/last-activity application))))

(defn- expiration-time [application]
  (when-some [delete-after (get-in env [:application-expiration
                                        (:application/state application)
                                        :delete-after])]
    (-> (get-last-activity application)
        (time/plus (Period/parse delete-after)))))

(defn- should-expire-application [application]
  (some-> (expiration-time application)
          (time/before? (time/now))))

(defn- expire-application [application]
  (when (should-expire-application application)
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
    (when (every? some? [delete-after reminder-before])
      (-> (:application/last-activity application)
          (time/plus (Period/parse delete-after))
          (time/minus (Period/parse reminder-before))))))

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

