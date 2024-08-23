(ns ^:integration rems.application.test-eraser
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.tools.logging.test :as log-test]
            [rems.application.commands :as commands]
            [rems.application.eraser :as eraser]
            [rems.application.expirer-bot :as expirer-bot]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.outbox :as outbox]
            [rems.db.roles :as roles]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.user-settings :as user-settings]
            [rems.testing-util :refer [with-fixed-time]]
            [clojure.string :as str]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

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

(defn- get-events [app-id]
  (:application/events (applications/get-application-internal app-id)))

(defn expiration-notifications-sent [event]
  (= :application.event/expiration-notifications-sent (:event/type event)))

(defn- get-all-application-ids [user-id]
  (->> (applications/get-all-applications user-id)
       (map :application/id)))

(deftest test-expire-application
  (let [_ (test-helpers/create-user! {:userid "alice"})
        draft (create-application! {:draft? true
                                    :date-time test-time
                                    :actor "alice"})
        old-submitted (create-application! {:date-time (time/minus test-time (time/days 120))
                                            :actor "alice"})
        expired-draft (create-application! {:draft? true
                                            :date-time (time/minus test-time (time/days 90) (time/seconds 1))
                                            :actor "alice"})
        outbox-emails (atom [])]

    (testing "does not process applications when expirer-bot user does not exist"
      (with-redefs [outbox/puts! (fn [emails] (swap! outbox-emails concat emails))
                    env {:application-expiration {:application.state/draft {:delete-after "P90D"}}}]
        (log-test/with-log
          (testing "processing applications does not delete applications"
            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
            (eraser/process-applications!)
            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
            (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/delete"))))

          (testing "expiration notification events are not created and emails are not sent"
            (is (empty? (filter expiration-notifications-sent (get-events draft))))
            (is (empty? (filter expiration-notifications-sent (get-events old-submitted))))
            (is (empty? (filter expiration-notifications-sent (get-events expired-draft))))
            (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
            (is (empty? @outbox-emails)))

          (is (log-test/logged? "rems.application.eraser" :warn "Cannot process applications, because user expirer-bot does not exist")))))

    (testing "does not delete applications when configuration is not valid"
      (with-redefs [outbox/puts! (fn [emails] (swap! outbox-emails concat emails))
                    env {}]
        (test-helpers/create-user! {:userid expirer-bot/bot-userid})
        (roles/add-role! expirer-bot/bot-userid :expirer)
        (applications/reload-cache!) ; we change roles which affect applications

        (log-test/with-log
          (testing "processing applications does not delete applications"
            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
            (eraser/process-applications!)
            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
            (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/delete"))))

          (testing "expiration notification events are not created and emails are not sent"
            (is (empty? (filter expiration-notifications-sent (get-events draft))))
            (is (empty? (filter expiration-notifications-sent (get-events old-submitted))))
            (is (empty? (filter expiration-notifications-sent (get-events expired-draft))))
            (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
            (is (empty? @outbox-emails)))

          (is (not (log-test/logged? "rems.application.eraser" :warn "Cannot process applications, because user expirer-bot does not exist")))
          (is (log-test/logged? "rems.application.eraser" :info "No applications to process")))))

    (testing "cannot remove other than draft applications"
      (with-redefs [outbox/puts! (fn [emails] (swap! outbox-emails concat emails))
                    env {:application-expiration {:application.state/submitted {:delete-after "P90D"}}}]
        (log-test/with-log
          (testing "processing applications does not delete applications"
            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
            (eraser/process-applications!)
            (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice")))))

          (testing "expiration notification events are not created and emails are not sent"
            (is (empty? (filter expiration-notifications-sent (get-events draft))))
            (is (empty? (filter expiration-notifications-sent (get-events old-submitted))))
            (is (empty? (filter expiration-notifications-sent (get-events expired-draft))))
            (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
            (is (empty? @outbox-emails)))

          (testing "attempt to delete submitted application is logged"
            (let [cmds (log-test/matches "rems.application.eraser" :info #"application.command/delete")
                  msg (:message (first cmds))]
              (is (= 1 (count cmds)))
              (is (str/includes? msg (str ":application-id " old-submitted))))

            (let [warnings (log-test/matches "rems.application.eraser" :warn #"Command validation failed")
                  msg (:message (first warnings))]
              (is (= 1 (count warnings)))
              (is (str/includes? msg ":application.command/delete"))
              (is (str/includes? msg (str ":application-id " old-submitted))))

            (is (not (log-test/logged? "rems.db.applications" :info #"Finished deleting application")))))))

    (testing "deletes expired draft application"
      (with-redefs [outbox/puts! (fn [emails] (swap! outbox-emails concat emails))
                    env {:application-expiration {:application.state/draft {:delete-after "P90D"}}}]
        (with-fixed-time test-time
          (log-test/with-log
            (testing "processing applications deletes expired draft"
              (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
              (eraser/process-applications!)
              (is (= #{draft old-submitted} (set (get-all-application-ids "alice")))))

            (testing "expiration notification events are not created and emails are not sent"
              (is (empty? (filter expiration-notifications-sent (get-events draft))))
              (is (empty? (filter expiration-notifications-sent (get-events old-submitted))))
              (is (empty? (filter expiration-notifications-sent (get-events expired-draft))))
              (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
              (is (empty? @outbox-emails)))

            (testing "application delete is logged"
              (let [cmds (log-test/matches "rems.application.eraser" :info #"application.command/delete")
                    msg (:message (first cmds))]
                (is (= 1 (count cmds)))
                (is (str/includes? msg (str ":application-id " expired-draft))))

              (let [deletes (log-test/matches "rems.db.applications" :info #"Finished deleting application")
                    msg (:message (first deletes))]
                (is (= 1 (count deletes)))
                (is (= (str "Finished deleting application " expired-draft) msg))))))))))

(deftest test-send-reminder-email
  (let [_ (test-helpers/create-user! {:userid "alice"})
        _ (test-helpers/create-user! {:userid "member"})
        _ (test-helpers/create-user! {:userid "expirer-bot"} :expirer)
        expiration-config {:application.state/draft {:delete-after "P90D"
                                                     :reminder-before "P7D"}}
        draft-expires-in-6d (create-application! {:draft? true
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
        test-time-plus-one-hour (time/plus test-time (time/hours 1))
        test-time-plus-ten-days (time/plus test-time (time/days 10))]

    (testing "should not send reminder because config is missing or partially correct,"
      (doseq [expiration [{:application.state/draft {:delete-after "P90D"}}
                          {:application.state/draft {:reminder-before "P7D"}}
                          nil]]
        (with-redefs [outbox/puts! (fn [emails] (swap! outbox-emails concat emails))
                      env {:application-id-column :id
                           :public-url "localhost/"
                           :application-expiration expiration}]
          (with-fixed-time test-time
            (log-test/with-log
              (testing "processing applications does not delete applications"
                (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
                (eraser/process-applications!)
                (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
                (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/delete"))))

              (testing "expiration notification events are not created and emails are not sent"
                (is (empty? (filter expiration-notifications-sent (get-events draft-expires-in-6d))))
                (is (empty? (filter expiration-notifications-sent (get-events draft-expires-in-8d))))
                (is (empty? (filter expiration-notifications-sent (get-events submitted))))
                (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/send-expiration-notifications")))
                (is (empty? @outbox-emails))))))))

    (testing "send expiration notifications"
      (with-redefs [outbox/puts! (fn [emails] (swap! outbox-emails concat emails))
                    user-settings/get-user-settings (constantly {:language :en})
                    env {:application-id-column :id
                         :public-url "localhost/"
                         :application-expiration expiration-config}]
        (with-fixed-time test-time
          (log-test/with-log
            (reset! outbox-emails [])

            (testing "processing applications does not delete applications"
              (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
              (eraser/process-applications!)
              (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
              (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/delete"))))

            (testing "expiration notification events are not created for non-expiring applications"
              (is (empty? (filter expiration-notifications-sent (get-events draft-expires-in-8d))))
              (is (empty? (filter expiration-notifications-sent (get-events submitted)))))

            (testing "expiration notification event is created for draft expiring in 6 days"
              (let [events (filter expiration-notifications-sent (get-events draft-expires-in-6d))
                    notification (first events)]
                (is (= 1 (count events)))
                (is (time/equal? (time/plus test-time (time/days 7)) (:application/expires-on notification)))
                (is (time/equal? test-time (:event/time notification))))

              (testing "emails are sent to applicants"
                (is (= [{:outbox/deadline test-time
                         :outbox/email {:subject (str "Your unsubmitted application " draft-expires-in-6d " will be deleted soon")
                                        :body (str "Dear alice,"
                                                   "\n\nYour unsubmitted application has been inactive since 2022-10-09 and it will be deleted after 2023-01-08, if it is not edited."
                                                   "\n\nYou can view and edit the application at localhost/application/" draft-expires-in-6d
                                                   "\n\nKind regards,\n\nREMS\n\n\nPlease do not reply to this automatically generated message.")
                                        :to-user "alice"}
                         :outbox/type :email}
                        {:outbox/deadline test-time
                         :outbox/email {:subject (str "Your unsubmitted application " draft-expires-in-6d " will be deleted soon")
                                        :body (str "Dear member,"
                                                   "\n\nYour unsubmitted application has been inactive since 2022-10-09 and it will be deleted after 2023-01-08, if it is not edited."
                                                   "\n\nYou can view and edit the application at localhost/application/" draft-expires-in-6d
                                                   "\n\nKind regards,\n\nREMS\n\n\nPlease do not reply to this automatically generated message.")
                                        :to-user "member"}
                         :outbox/type :email}]
                       @outbox-emails))))))

        (with-fixed-time test-time-plus-one-hour
          (log-test/with-log
            (reset! outbox-emails [])

            (testing "processing applications again after one hour does not delete applications"
              (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
              (eraser/process-applications!)
              (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
              (is (empty? (log-test/matches "rems.application.eraser" :info #"application.command/delete"))))

            (testing "expiration notification events are not created for non-expiring applications"
              (is (empty? (filter expiration-notifications-sent (get-events draft-expires-in-8d))))
              (is (empty? (filter expiration-notifications-sent (get-events submitted)))))

            (testing "expiration notifications events have not changed for draft expiring in 6 days"
              (let [events (filter expiration-notifications-sent (get-events draft-expires-in-6d))
                    notification (first events)]
                (is (= 1 (count events)))
                (is (time/equal? (time/plus test-time (time/days 7)) (:application/expires-on notification)))
                (is (time/equal? test-time (:event/time notification)))))

            (testing "email is not sent"
              (is (empty? @outbox-emails)))))

        ;; NB: gap between previous run simulates enabling eraser without previous notification messages.
        ;; some applications may already have expired, but without notification event, and when notifications
        ;; are enabled, those applications are expired only after "notification window".
        (with-fixed-time test-time-plus-ten-days
          (log-test/with-log
            (reset! outbox-emails [])

            (testing "processing applications again after ten days deletes expired draft application"
              (is (= #{draft-expires-in-6d draft-expires-in-8d submitted} (set (get-all-application-ids "alice"))))
              (eraser/process-applications!)
              (is (= #{draft-expires-in-8d submitted} (set (get-all-application-ids "alice")))))

            (testing "application delete is logged"
              (let [cmds (log-test/matches "rems.application.eraser" :info #"application.command/delete")
                    msg (:message (first cmds))]
                (is (= 1 (count cmds)))
                (is (str/includes? msg (str ":application-id " draft-expires-in-6d))))

              (is (not (log-test/logged? "rems.application.eraser" :warn #"Command validation failed")))

              (let [deletes (log-test/matches "rems.db.applications" :info #"Finished deleting application")
                    msg (:message (first deletes))]
                (is (= 1 (count deletes)))
                (is (= (str "Finished deleting application " draft-expires-in-6d) msg))))

            (testing "notifications are not created for non-expiring application"
              (is (empty? (filter expiration-notifications-sent (get-events submitted)))))

            (testing "notification is created for expiring draft"
              (let [events (filter expiration-notifications-sent (get-events draft-expires-in-8d))
                    notification (first events)]
                (is (= 1 (count events)))
                (is (time/equal? (time/plus test-time-plus-ten-days (time/days 7)) (:application/expires-on notification)))
                (is (time/equal? test-time-plus-ten-days (:event/time notification)))))

            (testing "email is sent to applicant for expiring draft"
              (is (= [{:outbox/deadline test-time-plus-ten-days
                       :outbox/email {:subject (str "Your unsubmitted application " draft-expires-in-8d " will be deleted soon")
                                      :body (str "Dear alice,"
                                                 "\n\nYour unsubmitted application has been inactive since 2022-10-11 and it will be deleted after 2023-01-18, if it is not edited."
                                                 "\n\nYou can view and edit the application at localhost/application/" draft-expires-in-8d
                                                 "\n\nKind regards,\n\nREMS\n\n\nPlease do not reply to this automatically generated message.")
                                      :to-user "alice"}
                       :outbox/type :email}]
                     @outbox-emails)))))))))
