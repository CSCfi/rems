(ns ^:integration rems.application.test-eraser
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [rems.application.commands :as commands]
            [rems.application.eraser :as eraser]
            [rems.application.expirer-bot :as expirer-bot]
            [rems.service.command :as command]
            [rems.config :refer [env]]
            [rems.locales]
            [rems.db.applications :as applications]
            [rems.db.outbox :as outbox]
            [rems.db.roles :as roles]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.user-settings :as user-settings]
            [rems.testing-util :refer [with-fixed-time]]))

(defn- empty-footer [f]
  (with-redefs [rems.locales/translations (assoc-in rems.locales/translations [:en :t :email :footer] "")]
    (f)))

(defn- empty-signature [f]
  (with-redefs [rems.locales/translations (assoc-in rems.locales/translations [:en :t :email :regards] "")]
    (f)))

(use-fixtures :once test-db-fixture)
(use-fixtures :each empty-footer empty-signature rollback-db-fixture)

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

(defn- expiration-notification-events [app-id]
  (->> (applications/get-application-internal app-id)
       :application/events
       (filter (comp #{:application.event/expiration-notifications-sent}
                     :event/type))))

(defn- get-all-application-ids [user-id]
  (->> (applications/get-all-applications user-id)
       (map :application/id)))

(defn- log*-mock [coll-atom]
  (fn [logger level throwable message]
    (swap! coll-atom conj {:logger logger :level level :throwable throwable :message message})))

(deftest test-expire-application
  (binding [command/*fail-on-process-manager-errors* true]
    (test-helpers/create-user! {:userid "alice"})
    (let [now (time/now)
          draft (create-application! {:draft? true
                                      :date-time now
                                      :actor "alice"})
          old-submitted (create-application! {:date-time (time/minus now (time/days 120))
                                              :actor "alice"})
          expired-draft (create-application! {:draft? true
                                              :date-time (time/minus now (time/days 90) (time/seconds 1))
                                              :actor "alice"})
          outbox-emails (atom [])
          log-messages (atom [])]
      (with-redefs [log/log* (log*-mock log-messages)
                    outbox/put! (fn [email] (swap! outbox-emails conj email))]

        (testing "does not process applications when expirer-bot user does not exist"
          (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
          (with-redefs [env {:application-expiration {:application.state/draft {:delete-after "P90D"}}}]
            (eraser/process-applications!))
          (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
          (is (empty? @outbox-emails))
          (is (= [(str "Cannot process applications, because user " expirer-bot/bot-userid " does not exist")]
                 (->> @log-messages
                      (filter (comp #{:warn} :level))
                      (map :message)))))

        (test-helpers/create-user! {:userid expirer-bot/bot-userid})
        (roles/add-role! expirer-bot/bot-userid :expirer)

        (testing "does not delete applications when configuration is not valid"
          (reset! log-messages [])
          (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
          (with-redefs [env {}]
            (eraser/process-applications!))
          (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
          (is (empty? @outbox-emails))
          (is (empty? (->> @log-messages
                           (filter (comp #{:warn} :level))))))

        (testing "removes expired applications"
          ;; expirer-bot user has been created previously
          (reset! log-messages [])
          (is (= #{draft old-submitted expired-draft} (set (get-all-application-ids "alice"))))
          (with-redefs [env {:application-expiration {:application.state/draft {:delete-after "P90D"}}}]
            (eraser/process-applications!))
          (is (= #{draft old-submitted} (set (get-all-application-ids "alice"))))
          (is (empty? @outbox-emails))
          (is (empty? (->> @log-messages
                           (filter (comp #{:warn} :level))))))

        (testing "cannot remove other than draft applications"
          ;; expirer-bot user has been created previously
          (is (= #{draft old-submitted} (set (get-all-application-ids "alice"))))
          (with-redefs [env {:application-expiration {:application.state/submitted {:delete-after "P90D"}}}]
            (is (thrown-with-msg? java.lang.AssertionError
                                  (re-pattern (str "Tried to delete application "
                                                   old-submitted
                                                   " which is not a draft"))
                                  (eraser/process-applications!))))
          (is (= #{draft old-submitted} (set (get-all-application-ids "alice")))))))))

(deftest test-send-reminder-email
  (binding [command/*fail-on-process-manager-errors* true]
    (with-fixed-time (time/date-time 2022)
      (fn []
        (test-helpers/create-user! {:userid "alice"})
        (test-helpers/create-user! {:userid "member"})
        (test-helpers/create-user! {:userid expirer-bot/bot-userid})
        (roles/add-role! expirer-bot/bot-userid :expirer)
        (let [now (time/now)
              draft-expires-in-6d (create-application! {:draft? true
                                                        :date-time (time/minus now (time/days 84))
                                                        :actor "alice"
                                                        :members [{:userid "member"
                                                                   :name "Member"
                                                                   :email "member@example.com"}]})
              draft-expires-in-8d (create-application! {:draft? true
                                                        :date-time (time/minus now (time/days 82))
                                                        :actor "alice"})
              submitted (create-application! {:date-time (time/minus now (time/days 84))
                                              :actor "alice"})
              outbox-emails (atom [])]
          (with-redefs [outbox/put! (fn [email] (swap! outbox-emails conj email))]

            (testing "should not send reminder because config is missing or partially correct"
              (doseq [mock-env [{:application-expiration {:application.state/draft {:delete-after "P90D"}}}
                                {:application-expiration {:application.state/draft {:reminder-before "P7D"}}}
                                {:application-expiration nil}]]
                (with-redefs [env mock-env]
                  (eraser/process-applications!))
                (is (= #{draft-expires-in-6d draft-expires-in-8d submitted}
                       (set (get-all-application-ids "alice"))))
                (is (empty? @outbox-emails))
                (is (empty? (expiration-notification-events draft-expires-in-6d)))
                (is (empty? (expiration-notification-events draft-expires-in-8d)))
                (is (empty? (expiration-notification-events submitted)))))

            (testing "send expiration notifications"
              (with-redefs [env {:application-expiration {:application.state/draft {:delete-after "P90D"
                                                                                    :reminder-before "P7D"}}
                                 :application-id-column :id
                                 :public-url "localhost/"}
                            user-settings/get-user-settings (constantly {:language :en})]
                (eraser/process-applications!)
                (is (= #{draft-expires-in-6d draft-expires-in-8d submitted}
                       (set (get-all-application-ids "alice")))
                    "no applications have been deleted")
                (is (empty? (expiration-notification-events draft-expires-in-8d)))
                (is (empty? (expiration-notification-events submitted)))

                (testing "expiration notifications sent event was created for draft expiring in 6 days"
                  (let [events (expiration-notification-events draft-expires-in-6d)]
                    (is (= 1 (count events)))
                    (is (time/equal? (time/plus now (time/days 6)) (:expires-on (first events))))
                    (is (time/equal? (time/minus now (time/days 84)) (:last-activity (first events))))
                    (is (time/equal? now (:event-time (first events))))))
                (is (= [{:outbox/deadline (time/date-time 2022)
                         :outbox/email {:subject (str "Your unsubmitted application " draft-expires-in-6d " will be deleted soon")
                                        :body (str "Dear alice,\n\n"
                                                   "Your unsubmitted application has been inactive since 2021-10-09 and it will be deleted after 2022-01-07, if it is not edited.\n\n"
                                                   "You can view and edit the application at localhost/application/" draft-expires-in-6d)
                                        :to-user "alice"}
                         :outbox/type :email}
                        {:outbox/deadline (time/date-time 2022)
                         :outbox/email {:subject (str "Your unsubmitted application " draft-expires-in-6d " will be deleted soon")
                                        :body (str "Dear member,\n\n"
                                                   "Your unsubmitted application has been inactive since 2021-10-09 and it will be deleted after 2022-01-07, if it is not edited.\n\n"
                                                   "You can view and edit the application at localhost/application/" draft-expires-in-6d)
                                        :to-user "member"}
                         :outbox/type :email}]
                       @outbox-emails))

                (testing "processing applications again should not send new notifications"
                  (reset! outbox-emails [])
                  (eraser/process-applications!)
                  (is (= #{draft-expires-in-6d draft-expires-in-8d submitted}
                         (set (get-all-application-ids "alice")))
                      "no applications have been deleted")
                  (is (empty? (expiration-notification-events draft-expires-in-8d)))
                  (is (empty? (expiration-notification-events submitted)))
                  (is (empty? @outbox-emails))

                  (testing "expiration notifications events have not changed for draft expiring in 6 days"
                    (let [events (expiration-notification-events draft-expires-in-6d)]
                      (is (= 1 (count events)))
                      (is (time/equal? (time/plus now (time/days 6)) (:expires-on (first events))))
                      (is (time/equal? (time/minus now (time/days 84)) (:last-activity (first events))))
                      (is (time/equal? now (:event-time (first events)))))))))))))))

