(ns rems.application.test-approver-bot
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.application.approver-bot :as approver-bot]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.application.test-model :as test-model])
  (:import [org.joda.time DateTime DateTimeUtils]))

(def test-time (DateTime. 10000))

(use-fixtures
  :once
  (fn [f]
    (DateTimeUtils/setCurrentMillisFixed (.getMillis test-time))
    (f)
    (DateTimeUtils/setCurrentMillisSystem)))

(defn dummy-blacklisted? [_user _resource]
  false)

(def injections
  (-> test-model/injections
      (assoc :blacklisted? dummy-blacklisted?)
      (update-in [:get-workflow 50 :workflow :handlers]
                 conj {:userid "approver-bot"
                       :name "Approver Bot"
                       :email "approver-bot"})))

(defn apply-events [events injections]
  (-> events
      events/validate-events
      (model/build-application-view injections)))

(deftest test-approver-bot
  (let [generate-commands #'approver-bot/generate-commands
        created-event {:event/type :application.event/created
                       :event/time (DateTime. 1000)
                       :event/actor "applicant"
                       :application/id 1
                       :application/external-id "2019/1"
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses []
                       :application/forms [{:form/id 40}]
                       :workflow/id 50
                       :workflow/type :workflow/default}
        submitted-event {:event/type :application.event/submitted
                         :event/time (DateTime. 2000)
                         :event/actor "applicant"
                         :application/id 1}
        approved-event {:event/type :application.event/approved
                        :event/time (DateTime. 3000)
                        :event/actor "handler"
                        :application/id 1
                        :application/comment ""}]

    (testing "approves submitted applications"
      (is (= [{:type :application.command/approve
               :actor "approver-bot"
               :time (time/now)
               :application-id 1
               :comment ""}]
             (generate-commands submitted-event (apply-events [created-event submitted-event] injections)))))

    (testing "does not approve if the applicant is blacklisted for one of the resources"
      (let [injections (assoc injections :blacklisted? (fn [user resource]
                                                         (= ["applicant" "urn:21"] [user resource])))
            application (apply-events [created-event submitted-event] injections)]
        (is (empty? (generate-commands submitted-event application)))))

    (testing "does not approve if a member is blacklisted for one of the resources"
      (let [member-added-event {:event/type :application.event/member-added
                                :event/time (DateTime. 2000)
                                :event/actor "handler"
                                :application/id 1
                                :application/member {:userid "member"}}
            injections (assoc injections :blacklisted? (fn [user resource]
                                                         (= ["member" "urn:11"] [user resource])))
            application (apply-events [created-event member-added-event submitted-event] injections)]
        (is (empty? (generate-commands submitted-event application)))))

    (testing "ignores applications in other states"
      (is (empty? (generate-commands created-event (apply-events [created-event] injections))))
      (is (empty? (generate-commands approved-event
                                     (apply-events [created-event submitted-event approved-event] injections)))))

    (testing "ignores applications where the bot is not a handler"
      (let [injections (update-in injections [:get-workflow 50 :workflow :handlers] empty)
            application (apply-events [created-event submitted-event] injections)]
        (is (empty? (generate-commands submitted-event application)))))))
