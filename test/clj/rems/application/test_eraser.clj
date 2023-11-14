(ns ^:integration rems.application.test-eraser
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.tools.logging.test :as log-test]
            [rems.application.commands :as commands]
            [rems.application.eraser :as eraser]
            [rems.application.expirer-bot :as expirer-bot]
            [rems.config :refer [env]]
            [rems.locales :refer [translations]]
            [rems.db.applications :as applications]
            [rems.db.outbox :as outbox]
            [rems.db.roles :as roles]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.user-settings :as user-settings]
            [rems.testing-util :refer [with-fixed-time]]
            [clojure.string :as str]))

(defn- email-footer-and-signature [f]
  (with-redefs [translations (update-in translations [:en :t :email]
                                        assoc :footer "" :regards "")]
    (f)))

(use-fixtures :once test-db-fixture)
(use-fixtures
  :each
  email-footer-and-signature
  rollback-db-fixture)

(def test-time (time/date-time 2023))

(defn- create-application!
  [{:keys [date-time actor draft? members]}]
  (let [res (test-helpers/create-resource! {:resource-ext-id "res"})
        wf (test-helpers/create-workflow! {:type :workflow/default})
        cat (test-helpers/create-catalogue-item! {:title {:en "cat"}
                                                  :workflow-id wf
                                                  :resource-id res})
        app-id (test-helpers/create-draft! actor [cat] "eraser-test" date-time)]
    (doseq [member members]
      (let [f commands/handle-command]
        (with-redefs [commands/handle-command (fn [cmd application injections]
                                                (->> (assoc injections
                                                            :secure-token (constantly "test"))
                                                     (f cmd application)))]
          (test-helpers/command! {:time date-time
                                  :type :application.command/invite-member
                                  :application-id app-id
                                  :member (dissoc member :userid)
                                  :actor actor})
          (test-helpers/command! {:time date-time
                                  :type :application.command/accept-invitation
                                  :application-id app-id
                                  :token "test"
                                  :actor (:userid member)}))))
    (when (not draft?)
      (test-helpers/submit-application {:application-id app-id
                                        :actor actor
                                        :time date-time}))
    app-id))

(defn- get-application-events [app-id]
  (:application/events (applications/get-application-internal app-id)))

(defn expiration-notifications-sent [event]
  (= :application.event/expiration-notifications-sent (:event/type event)))

(defn- get-all-application-ids [user-id]
  (->> (applications/get-all-applications user-id)
       (map :application/id)))

(deftest test-expire-application
  (test-helpers/create-user! {:userid "alice"})
  (let [draft (create-application! {:draft? true
                                    :date-time test-time
                                    :actor "alice"})
        old-submitted (create-application! {:date-time (time/minus test-time (time/days 120))
                                            :actor "alice"})
        expired-draft (create-application! {:draft? true
                                            :date-time (time/minus test-time (time/days 90) (time/seconds 1))
                                            :actor "alice"})
        outbox-emails (atom [])
        config {:application-expiration {:application.state/draft {:delete-after "P90D"}}}]
    (with-redefs [outbox/put! (fn [email] (swap! outbox-emails conj email))
                  env config]

      (testing "does not process applications when expirer-bot user does not exist"
        (log-test/with-log
          (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
          (eraser/process-applications!)

          (is (log-test/logged? "rems.application.eraser" :warn "Cannot process applications, because user expirer-bot does not exist"))
          (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
          (is (empty? @outbox-emails))
          (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/delete")))
          (is (not (log-test/logged? "rems.db.applications" :info #"Finished deleting application")))

          (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice")))
              "Applications should not have been deleted")))

      (testing "does not delete applications when configuration is not valid"
        (log-test/with-log
          (with-redefs [env {}]
            (test-helpers/create-user! {:userid expirer-bot/bot-userid})
            (roles/add-role! expirer-bot/bot-userid :expirer)

            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
            (eraser/process-applications!)

            (is (not (log-test/logged? "rems.application.eraser" :warn "Cannot process applications, because user expirer-bot does not exist")))
            (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
            (is (empty? @outbox-emails))
            (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/delete")))
            (is (not (log-test/logged? "rems.db.applications" :info #"Finished deleting application")))

            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice")))
                "applications should not have been deleted"))))

      (testing "cannot remove other than draft applications"
        (log-test/with-log
          (with-redefs [env {:application-expiration {:application.state/submitted {:delete-after "P90D"}}}]
            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
            (eraser/process-applications!)
            (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
            (is (empty? @outbox-emails))
            (is (not (log-test/logged? "rems.db.applications" :info #"Finished deleting application")))

            (testing "delete command start is logged"
              (let [cmds (log-test/matches "rems.application.eraser" :info #"application.command/delete")
                    msg (:message (first cmds))]
                (is (= 1 (count cmds)))
                (is (str/includes? msg (str ":application-id " old-submitted)))))

            (testing "delete command validation failure is logged"
              (let [warnings (log-test/matches "rems.application.eraser" :warn #"Command validation failed")
                    msg (:message (first warnings))]
                (is (= 1 (count warnings)))
                (is (str/includes? msg ":application.command/delete"))
                (is (str/includes? msg (str ":application-id " old-submitted)))))

            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice")))
                "applications should not have been deleted"))))

      (testing "removes expired applications"
        (log-test/with-log
          (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
          (with-fixed-time test-time
            (eraser/process-applications!))

          (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
          (is (empty? @outbox-emails))

          (testing "delete command start is logged"
            (let [cmds (log-test/matches "rems.application.eraser" :info #"application.command/delete")
                  msg (:message (first cmds))]
              (is (= 1 (count cmds)))
              (is (str/includes? msg (str ":application-id " expired-draft)))))

          (is (not (log-test/logged? "rems.application.eraser" :warn #"Command validation failed")))

          (testing "delete success is logged"
            (let [deletes (log-test/matches "rems.db.applications" :info #"Finished deleting application")
                  msg (:message (first deletes))]
              (is (= 1 (count deletes)))
              (is (= (str "Finished deleting application " expired-draft) msg))))

          (is (= #{draft old-submitted} (set (get-all-application-ids "alice")))
              "expired draft application is deleted"))))))

(deftest test-send-reminder-email
  (test-helpers/create-user! {:userid "alice"})
  (test-helpers/create-user! {:userid "member"})
  (test-helpers/create-user! {:userid expirer-bot/bot-userid})
  (roles/add-role! expirer-bot/bot-userid :expirer)
  (let [draft-expires-in-6d (create-application! {:draft? true
                                                  :date-time (time/minus test-time (time/days 84))
                                                  :actor "alice"
                                                  :members [{:userid "member"
                                                             :name "Member"
                                                             :email "member@example.com"}]})
        draft-expires-in-8d (create-application! {:draft? true
                                                  :date-time (time/minus test-time (time/days 82))
                                                  :actor "alice"})
        submitted (create-application! {:date-time (time/minus test-time (time/days 84))
                                        :actor "alice"})
        outbox-emails (atom [])
        config {:application-id-column :id
                :public-url "localhost/"
                :application-expiration {:application.state/draft {:delete-after "P90D"
                                                                   :reminder-before "P7D"}}}]
    (with-redefs [outbox/put! (fn [email] (swap! outbox-emails conj email))
                  user-settings/get-user-settings (constantly {:language :en})
                  env config]

      (testing "should not send reminder because config is missing or partially correct"
        (with-fixed-time test-time
          (doseq [expiration [{:application.state/draft {:delete-after "P90D"}}
                              {:application.state/draft {:reminder-before "P7D"}}
                              nil]]
            (with-redefs [env (assoc config :application-expiration expiration)]
              (eraser/process-applications!)
              (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
              (is (empty? @outbox-emails))
              (is (empty? (filter expiration-notifications-sent (get-application-events draft-expires-in-6d))))
              (is (empty? (filter expiration-notifications-sent (get-application-events draft-expires-in-8d))))
              (is (empty? (filter expiration-notifications-sent (get-application-events submitted))))))))

      (testing "send expiration notifications"
        (with-fixed-time test-time
          (eraser/process-applications!)
          (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice")))
              "no applications have been deleted")
          (is (empty? (filter expiration-notifications-sent (get-application-events draft-expires-in-8d))))
          (is (empty? (filter expiration-notifications-sent (get-application-events submitted))))

          (testing "expiration notifications sent event was created for draft expiring in 6 days"
            (let [events (filter expiration-notifications-sent (get-application-events draft-expires-in-6d))]
              (is (= 1 (count events)))
              (is (time/equal? (time/plus test-time (time/days 6)) (:application/expires-on (first events))))
              (is (time/equal? test-time (:event-time (first events))))))

          (is (= [{:outbox/deadline test-time
                   :outbox/email {:subject (str "Your unsubmitted application " draft-expires-in-6d " will be deleted soon")
                                  :body (str "Dear alice,\n\n"
                                             "Your unsubmitted application has been inactive since 2022-10-09 and it will be deleted after 2023-01-07, if it is not edited.\n\n"
                                             "You can view and edit the application at localhost/application/" draft-expires-in-6d)
                                  :to-user "alice"}
                   :outbox/type :email}
                  {:outbox/deadline test-time
                   :outbox/email {:subject (str "Your unsubmitted application " draft-expires-in-6d " will be deleted soon")
                                  :body (str "Dear member,\n\n"
                                             "Your unsubmitted application has been inactive since 2022-10-09 and it will be deleted after 2023-01-07, if it is not edited.\n\n"
                                             "You can view and edit the application at localhost/application/" draft-expires-in-6d)
                                  :to-user "member"}
                   :outbox/type :email}]
                 @outbox-emails))

          (testing "processing applications again should not send new notifications"
            (reset! outbox-emails [])
            (eraser/process-applications!)

            (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice")))
                "no applications have been deleted")
            (is (empty? (filter expiration-notifications-sent (get-application-events draft-expires-in-8d))))
            (is (empty? (filter expiration-notifications-sent (get-application-events submitted))))
            (is (empty? @outbox-emails))

            (testing "expiration notifications events have not changed for draft expiring in 6 days"
              (let [events (filter expiration-notifications-sent (get-application-events draft-expires-in-6d))]
                (is (= 1 (count events)))
                (is (time/equal? (time/plus test-time (time/days 6)) (:application/expires-on (first events))))
                (is (time/equal? test-time (:event-time (first events)))))))))

      (testing "draft is expired after extended reminder period"
        (with-fixed-time (-> test-time
                             (time/plus (time/days 7))
                             (time/plus (time/seconds 1)))
          (log-test/with-log
            (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
            (eraser/process-applications!)

            (testing "delete command start is logged"
              (let [cmds (log-test/matches "rems.application.eraser" :info #"application.command/delete")
                    msg (:message (first cmds))]
                (is (= 1 (count cmds)))
                (is (str/includes? msg (str ":application-id " draft-expires-in-6d)))))

            (is (not (log-test/logged? "rems.application.eraser" :warn #"Command validation failed")))

            (testing "delete success is logged"
              (let [deletes (log-test/matches "rems.db.applications" :info #"Finished deleting application")
                    msg (:message (first deletes))]
                (is (= 1 (count deletes)))
                (is (= (str "Finished deleting application " draft-expires-in-6d) msg))))

            (is (= #{draft-expires-in-8d submitted} (set (get-all-application-ids "alice")))
                "draft application is deleted")))))))
