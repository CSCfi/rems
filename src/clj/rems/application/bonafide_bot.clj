(ns rems.application.bonafide-bot
  "A bot that enables workflows where a user can ask another user to vouch
   for their bona fide researcher status."
  (:require [clojure.test :refer [deftest is testing]]
            [clj-time.core :as time]
            [rems.common.application-util :as application-util]
            [rems.db.applications :as applications]
            [rems.db.users :as users]
            [rems.testing-util :refer [with-fixed-time]]))

(def bot-userid "bonafide-bot")

(defn- find-email-address [application]
  (some (fn [field]
          (when (= :email (:field/type field))
            (:field/value field)))
        (mapcat :form/fields
                (:application/forms application))))

(defn- may-give-bonafide-status? [user-attributes]
  (contains? #{"so" "system"} (:researcher-status-by user-attributes)))

(defn- generate-commands [event actor-attributes application]
  (when (application-util/is-handler? application bot-userid)
    (case (:event/type event)
      :application.event/submitted
      (let [email (find-email-address application)]
        (assert email (pr-str application))
        [{:type :application.command/invite-decider
          :time (time/now)
          :application-id (:application/id event)
          :actor bot-userid
          :decider {:name "Referer"
                    :email email}}])
      :application.event/decided
      (when (may-give-bonafide-status? actor-attributes)
        [{:type (case (:application/decision event)
                  :approved :application.command/approve
                  :rejected :application.command/reject)
          :time (time/now)
          :application-id (:application/id event)
          :actor bot-userid}])

      [])))

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
            (is (empty? (generate-commands event
                                           applicant-attributes
                                           {:application/workflow {:workflow.dynamic/handlers [{:userid "handler"}]}
                                            :application/forms forms}))))
          (testing "bot is handler"
            (is (= [{:type :application.command/invite-decider
                     :time (time/date-time 2010)
                     :application-id 1234
                     :actor "bonafide-bot"
                     :decider {:name "Referer" :email "referer92@example.com"}}]
                   (generate-commands event
                                      applicant-attributes
                                      {:application/workflow {:workflow.dynamic/handlers [{:userid "handler"}
                                                                                          {:userid bot-userid}]}
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
            (is (empty? (generate-commands event
                                           referer-attributes
                                           {:application/workflow {:workflow.dynamic/handlers [{:userid "handler"}]}}))))
          (testing "bot is handler,"
            (let [application {:application/workflow {:workflow.dynamic/handlers [{:userid bot-userid}]}}]
              (testing "referer does not have researcher status"
                (is (empty? (generate-commands event referer-attributes application))))
              (testing "referer has researcher status,"
                (let [referer-attributes (assoc referer-attributes :researcher-status-by "so")]
                  (testing "refer approves"
                    (is (= [{:type :application.command/approve
                             :time (time/date-time 2010)
                             :application-id 1234
                             :actor "bonafide-bot"}]
                           (generate-commands event referer-attributes application))))
                  (testing "referer rejects"
                    (is (= [{:type :application.command/reject
                             :time (time/date-time 2010)
                             :application-id 1234
                             :actor "bonafide-bot"}]
                           (generate-commands (assoc event :application/decision :rejected) referer-attributes application)))))))))))))



(defn run-bonafide-bot [new-events]
  (doall (mapcat #(generate-commands %
                                     (users/get-user (:event/actor %))
                                     (applications/get-application (:application/id %)))
                 new-events)))
