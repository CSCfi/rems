(ns rems.email.test-core
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.config]
            [rems.email.core :refer [send-email! handle-send-error!]]
            [clj-time.core :as time])
  (:import [com.icegreen.greenmail.util GreenMail ServerSetup]))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'rems.config/env)
    (f)
    (mount/stop)))

(deftest test-send-email!
  ;; Just for a bit of coverage in code that doesn't get run in other tests or the dev profile
  (let [message-atom (atom nil)]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :smtp-host "localhost"
                                         :smtp-port 25
                                         :mail-from "rems@rems.rems")
                  postal.core/send-message (fn [_host message] (reset! message-atom message))
                  rems.db.users/get-user (constantly {:email "user@example.com"})
                  rems.db.user-settings/get-user-settings (constantly {})
                  rems.db.outbox/attempt-failed! (fn [email error])
                  rems.db.outbox/attempt-failed-fatally! (fn [email error])]

      (testing "don't send if there is no body (mail toggled off)"
        (is (nil? (send-email! {:to "foo@example.com" :subject "ding" :body nil})))
        (is (= nil @message-atom))
        (reset! message-atom nil))

      (testing "mail to email address"
        (is (nil? (send-email! {:to "foo@example.com" :subject "ding" :body "boing"})))
        (is (= {:to "foo@example.com"
                :subject "ding"
                :body "boing"
                :from "rems@rems.rems"
                "Auto-Submitted" "auto-generated"}
               @message-atom))
        (reset! message-atom nil))

      (testing "mail to user"
        (is (nil? (send-email! {:to-user "user" :subject "x" :body "y"})))
        (is (= {:to "user@example.com"
                :to-user "user"
                :subject "x"
                :body "y"
                :from "rems@rems.rems"
                "Auto-Submitted" "auto-generated"}
               @message-atom))
        (reset! message-atom nil))

      (testing "mail to user with notification email in user settings"
        (with-redefs [rems.db.user-settings/get-user-settings (constantly {:notification-email "alternative@example.com"})]
          (is (nil? (send-email! {:to-user "user" :subject "x" :body "y"}))))
        (is (= {:to "alternative@example.com"
                :to-user "user"
                :subject "x"
                :body "y"
                :from "rems@rems.rems"
                "Auto-Submitted" "auto-generated"}
               @message-atom))
        (reset! message-atom nil))

      (testing "mail to user without email"
        (with-redefs [rems.db.users/get-user (constantly {:email nil})]
          (is (nil? (send-email! {:to-user "user" :subject "x" :body "y"}))))
        (is (nil? @message-atom))
        (reset! message-atom nil))

      (testing "invalid email address"
        (let [email {:to "fake email" :subject "x" :body "y"}
              entry {:outbox/email email}
              error (send-email! email)]
          (is (= "failed address validation: Invalid address \"fake email\": javax.mail.internet.AddressException: Local address contains control or whitespace in string ``fake email''"
                 error))
          (is (nil? @message-atom))
          (is (= :rems.email.core/giving-up-after-fatal-error (handle-send-error! entry error)))
          (reset! message-atom nil)))

      (testing "invalid email address"
        (let [email {:to "foo.bar;@example.com" :subject "x" :body "y"}
              entry {:outbox/email email}
              error (send-email! email)]
          (is (= "failed address validation: Invalid address \"foo.bar;@example.com\": javax.mail.internet.AddressException: Illegal semicolon, not in group in string ``foo.bar;@example.com'' at position 7"
                 error))
          (is (nil? @message-atom))
          (is (= :rems.email.core/giving-up-after-fatal-error (handle-send-error! entry error)))
          (reset! message-atom nil)))

      (testing "failed send"
        (with-redefs [postal.core/send-message (fn [_host _message]
                                                 (throw (Throwable. "dummy exception")))]
          (let [email {:to-user "user" :subject "x" :body "y"}
                entry {:outbox/email email}
                error (send-email! email)]
            (is (= "failed sending email: java.lang.Throwable: dummy exception"
                   error))
            (is (nil? @message-atom))
            ;; NB: we could test retry but that is for outbox tests
            ;; our dummy does not have next attempt
            (is (= :rems.email.core/giving-up-after-all-attempts (handle-send-error! entry error)))
            (reset! message-atom nil))))

      (testing "SMTP not configured"
        (with-redefs [rems.config/env (assoc rems.config/env :smtp-host nil)]
          (is (nil? (send-email! {:to-user "user" :subject "x" :body "y"}))
              "should skip the message quietly"))
        (is (nil? @message-atom))
        (reset! message-atom nil)))))

(deftest test-send-email!-mock-smtp
  ;; run one case with an smtp server to make sure the Auto-Submitted header is set
  (let [port 3025
        server (GreenMail. (ServerSetup. port nil ServerSetup/PROTOCOL_SMTP))]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :smtp-host "localhost"
                                         :smtp-port port
                                         :mail-from "test@example.com")]
      (try
        (.start server)
        (send-email! {:to "target@example.com"
                      :subject "Test subject"
                      :body "Test email body."})
        (let [messages (.getReceivedMessages server)
              message (first messages)]
          (is (= 1 (count messages)))
          (is (= "Test subject" (.getSubject message)))
          (is (= ["target@example.com"] (map str (.getAllRecipients message))))
          (is (= ["auto-generated"] (vec (.getHeader message "Auto-Submitted")))))
        (finally
          (.stop server))))))

(deftest test-smtp-configuration
  (let [port 3025
        server (GreenMail. (ServerSetup. port nil ServerSetup/PROTOCOL_SMTP))]
    (.setUser server "test@example.com" "user" "password")

    (with-redefs [rems.config/env (assoc rems.config/env
                                         :smtp {:host "localhost"
                                                :port port
                                                :user "user"
                                                :pass "password"}
                                         :mail-from "test@example.com")]
      (try
        (.start server)
        (send-email! {:to "target@example.com"
                      :subject "Test subject"
                      :body "Test email body."})
        (let [messages (.getReceivedMessages server)
              message (first messages)]
          (is (= 1 (count messages)))
          (is (= "Test subject" (.getSubject message))))
        (finally
          (.stop server))))))
