(ns rems.workflow.dynamic
  (:require [clojure.test :refer :all]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.util :refer [getx]]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;;; Schemas

(def UserId s/Str)

(def Event
  {:actor UserId
   :application-id Long
   :event s/Keyword
   :comment (s/maybe s/Str)
   :time DateTime})

(def Command
  {:type s/Keyword
   :actor UserId
   :application-id Long
   :time DateTime})

(def Workflow
  {:type :workflow/dynamic
   :handlers [UserId]})

(def States #{::draft ::submitted ::approved ::rejected ::closed})
(def CommandTypes #{::submit ::return ::accept-license ::require-license ::request-decision ::decide ::request-comment ::comment ::approve ::reject ::close})
(def EventTypes #{:event/submitted :event/returned :event/license-required :event/license-accepted :event/comment-requested :event/commented :event/decision-requested :event/decided :event/approved :event/rejected :event/closed})





;;; Events

(defmulti ^:private apply-event
  "Applies an event to an application state."
  ;; dispatch by event type
  (fn [_application workflow event] [(:event event) (:type workflow)]))

(defn get-event-types
  "Fetch sequence of supported event names."
  []
  (map first (keys (methods apply-event))))

(deftest test-all-event-types-handled
  (is (= EventTypes (set (get-event-types)))))

(defmethod apply-event [:event/submitted :workflow/dynamic]
  [application workflow event]
  (assoc application :state ::submitted ))

(defmethod apply-event [:event/approved :workflow/dynamic]
  [application workflow event]
  (assoc application :state ::approved))

(defmethod apply-event [:event/returned :workflow/dynamic]
  [application workflow event]
  (assoc application :state ::returned))

(defn- apply-events [application events]
  (reduce apply-event application events))




;;; Commands
(defmulti ^:private handle-command
  "Handles a command by an event."
  (fn [cmd _application] (:type cmd)))

(defn get-command-types
  "Fetch sequence of supported command names."
  []
  (keys (methods handle-command)))

(deftest test-all-command-types-handled
  (is (= CommandTypes (set (get-command-types)))))

(defn impossible-command? [cmd application]
  (let [result (handle-command cmd application)]
    (when-not (:success result)
      result)))

(defmethod handle-command ::submit
  [cmd application]
  (cond (not= (:actor cmd) (:applicantuserid application)) {:errors [:unauthorized]}
        (not (contains? #{::draft ::returned} (:state application))) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/submitted
                        :actor (:actor cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defmethod handle-command ::approve
  [cmd application]
  (cond (not (contains? (set (:handlers (:workflow application))) (:actor cmd)))  {:errors [:unauthorized]}
        (not= ::submitted (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/approved
                        :actor (:actor cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defmethod handle-command ::reject
  [cmd application]
  (cond (not (contains? (set (:handlers (:workflow application))) (:actor cmd)))  {:errors [:unauthorized]}
        (not= ::submitted (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/rejected
                        :actor (:actor cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defmethod handle-command ::return
  [cmd application]
  (cond (not (contains? (set (:handlers (:workflow application))) (:actor cmd)))  {:errors [:unauthorized]}
        (not= ::submitted (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/returned
                        :actor (:actor cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defn- apply-command [application cmd]
  (let [result (handle-command cmd application)]
    (assert (:success result) (pr-str result))
    (apply-event application (:workflow application) (getx result :result))))

(defn- apply-commands [application commands]
  (reduce apply-command application commands))





;;; Tests

(deftest test-submit-approve
  (let [application {:state ::draft
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}]
    (is (= ::approved (:state (apply-commands application
                                              [{:actor "applicant" :type ::submit}
                                               {:actor "assistant" :type ::approve}]))))))

(deftest test-submit-return-submit-approve
  (let [application {:state ::draft
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        returned-application (apply-commands application
                                             [{:actor "applicant" :type ::submit}
                                              {:actor "assistant" :type ::return}])
        approved-application (apply-commands returned-application [{:actor "applicant" :type ::submit}
                                                                   {:actor "assistant" :type ::approve}])]
    (is (= ::returned (:state returned-application)))
    (is (= ::approved (:state approved-application)))))
