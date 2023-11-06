(ns rems.application.test-bona-fide-bot
  (:require [clj-time.core :as time]
            [clojure.test :refer [deftest is testing]]
            [rems.application.bona-fide-bot :as bona-fide-bot]
            [rems.testing-util :refer [with-fixed-time]]))

(deftest test-find-email-address
  (testing "no email field"
    (is (nil?
         (#'bona-fide-bot/find-email-address {:application/forms [{:form/id 1
                                                                   :form/fields [{:field/id 1
                                                                                  :field/type :text
                                                                                  :field/value "xxx"}
                                                                                 {:field/id 2
                                                                                  :field/type :date
                                                                                  :field/value "2020-01-01"}]}]}))))
  (testing "unique email field"
    (is (= "user@example.com"
           (#'bona-fide-bot/find-email-address {:application/forms [{:form/id 1
                                                                     :form/fields [{:field/id 1
                                                                                    :field/type :text
                                                                                    :field/value "xxx"}
                                                                                   {:field/id 2
                                                                                    :field/type :email
                                                                                    :field/value "user@example.com"}
                                                                                   {:field/id 3
                                                                                    :field/type :date
                                                                                    :field/value "2020-01-01"}]}]}))))
  (testing "multiple email fields"
    (is (= "user@example.com"
           (#'bona-fide-bot/find-email-address {:application/forms [{:form/id 1
                                                                     :form/fields [{:field/id 1
                                                                                    :field/type :text
                                                                                    :field/value "xxx"}
                                                                                   {:field/id 2
                                                                                    :field/type :email
                                                                                    :field/value "user@example.com"}
                                                                                   {:field/id 3
                                                                                    :field/type :date
                                                                                    :field/value "2020-01-01"}
                                                                                   {:field/id 4
                                                                                    :field/type :email
                                                                                    :field/value "wrong@example.com"}]}]}))))
  (testing "multiple forms"
    (is (= "user@example.com"
           (#'bona-fide-bot/find-email-address {:application/forms [{:form/id 1
                                                                     :form/fields [{:field/id 1
                                                                                    :field/type :text
                                                                                    :field/value "xxx"}]}
                                                                    {:form/id 2
                                                                     :form/fields [{:field/id 2
                                                                                    :field/type :email
                                                                                    :field/value "user@example.com"}
                                                                                   {:field/id 3
                                                                                    :field/type :date
                                                                                    :field/value "2020-01-01"}]}
                                                                    {:form/id 3
                                                                     :form/fields [{:field/id 4
                                                                                    :field/type :email
                                                                                    :field/value "wrong@example.com"}]}]})))))

(deftest test-generate-commands
  (with-fixed-time (time/date-time 2010)
    (fn []
      (testing "submitted event,"
        (let [event {:event/type :application.event/submitted
                     :event/actor "applicant"
                     :application/id 1234}
              applicant-attributes {:userid "applicant"
                                    :email "applicant@example.com"
                                    :name "An Applicant"}
              forms [{:form/fields [{:field/type :text
                                     :field/value "this is text"}
                                    {:field/type :email
                                     :field/value "referer92@example.com"}
                                    {:field/type :date
                                     :field/value "2020-01-01"}]}]]
          (testing "bot not handler"
            (is (empty? (#'bona-fide-bot/generate-commands event
                                                           applicant-attributes
                                                           {:application/workflow {:workflow.dynamic/handlers [{:userid "handler"}]}
                                                            :application/forms forms}))))
          (testing "bot is handler"
            (is (= [{:type :application.command/invite-decider
                     :time (time/date-time 2010)
                     :application-id 1234
                     :actor "bona-fide-bot"
                     :decider {:name "Referer" :email "referer92@example.com"}}]
                   (#'bona-fide-bot/generate-commands event
                                                      applicant-attributes
                                                      {:application/workflow {:workflow.dynamic/handlers [{:userid "handler"}
                                                                                                          {:userid "bona-fide-bot"}]}
                                                       :application/forms forms}))))))
      (testing "decided event,"
        (let [event {:event/type :application.event/decided
                     :event/actor "referer"
                     :application/decision :approved
                     :application/id 1234}
              referer-attributes {:userid "referer"
                                  :email "refer2000@example.com"
                                  :name "Ref Errer"}]
          (testing "bot not handler"
            (is (empty? (#'bona-fide-bot/generate-commands event
                                                           referer-attributes
                                                           {:application/workflow {:workflow.dynamic/handlers [{:userid "handler"}]}}))))
          (testing "bot is handler,"
            (let [application {:application/workflow {:workflow.dynamic/handlers [{:userid "bona-fide-bot"}]}}]
              (testing "referer does not have researcher status"
                (is (= [{:type :application.command/reject
                         :time (time/date-time 2010)
                         :application-id 1234
                         :actor "bona-fide-bot"}]
                       (#'bona-fide-bot/generate-commands event referer-attributes application))))
              (testing "referer has researcher status,"
                (let [referer-attributes (assoc referer-attributes :researcher-status-by "so")]
                  (testing "referer approves"
                    (is (= [{:type :application.command/approve
                             :time (time/date-time 2010)
                             :application-id 1234
                             :actor "bona-fide-bot"}]
                           (#'bona-fide-bot/generate-commands event referer-attributes application))))
                  (testing "referer rejects"
                    (is (= [{:type :application.command/reject
                             :time (time/date-time 2010)
                             :application-id 1234
                             :actor "bona-fide-bot"}]
                           (#'bona-fide-bot/generate-commands (assoc event :application/decision :rejected) referer-attributes application)))))))))))))
