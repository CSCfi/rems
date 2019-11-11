(ns rems.email.test-core
  (:require [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.config]
            [rems.email.core :refer [send-email!]]))

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
                  rems.db.users/get-user (constantly {:email "user@example.com"})]

      (testing "mail to email address"
        (is (nil? (send-email! {:to "foo@example.com" :subject "ding" :body "boing"})))
        (is (= {:to "foo@example.com"
                :subject "ding"
                :body "boing"
                :from "rems@rems.rems"}
               @message-atom))
        (reset! message-atom nil))

      (testing "mail to user"
        (is (nil? (send-email! {:to-user "user" :subject "x" :body "y"})))
        (is (= {:to "user@example.com"
                :to-user "user"
                :subject "x"
                :body "y"
                :from "rems@rems.rems"}
               @message-atom))
        (reset! message-atom nil))

      (testing "mail to user without email"
        (with-redefs [rems.db.users/get-user (constantly {:email nil})]
          (is (nil? (send-email! {:to-user "user" :subject "x" :body "y"}))))
        (is (nil? @message-atom))
        (reset! message-atom nil))

      (testing "invalid email address"
        (is (= "failed address validation: Invalid address \"fake email\": javax.mail.internet.AddressException: Local address contains control or whitespace in string ``fake email''"
               (send-email! {:to "fake email" :subject "x" :body "y"})))
        (is (nil? @message-atom))
        (reset! message-atom nil))

      (testing "failed send"
        (with-redefs [postal.core/send-message (fn [_host _message]
                                                 (throw (Throwable. "dummy exception")))]
          (is (= "failed sending email: java.lang.Throwable: dummy exception"
                 (send-email! {:to-user "user" :subject "x" :body "y"})))))

      (testing "SMTP not configured"
        (with-redefs [rems.config/env (assoc rems.config/env :smtp-host nil)]
          (is (nil? (send-email! {:to-user "user" :subject "x" :body "y"}))
              "should skip the message quietly"))
        (is (nil? @message-atom))
        (reset! message-atom nil)))))
