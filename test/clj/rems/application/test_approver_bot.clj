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

(def injections
  (-> test-model/injections
      (update-in [:get-workflow 50 :workflow :handlers]
                 conj {:userid "approver-bot"
                       :name "Approver Bot"
                       :email "approver-bot"})))

(defn apply-events [events injections]
  (-> events
      events/validate-events
      (model/build-application-view injections)))

(deftest test-approver-bot
  (let [created-event {:event/type :application.event/created
                       :event/time (DateTime. 1000)
                       :event/actor "applicant"
                       :application/id 1
                       :application/external-id "2019/1"
                       :application/resources []
                       :application/licenses []
                       :form/id 40
                       :workflow/id 50
                       :workflow/type :workflow/dynamic}
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
             (approver-bot/generate-commands
              (apply-events [created-event submitted-event] injections)))))

    (testing "ignores applications in other states"
      (is (empty? (approver-bot/generate-commands
                   (apply-events [created-event] injections))))
      (is (empty? (approver-bot/generate-commands
                   (apply-events [created-event submitted-event approved-event] injections)))))

    (testing "ignores applications where the bot is not a handler"
      (is (empty? (approver-bot/generate-commands
                   (apply-events [created-event submitted-event] test-model/injections)))))))
