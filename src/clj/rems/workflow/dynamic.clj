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

(def RequestDecisionCommand
  (assoc Command
         :decider UserId))

(def DecisionCommand
  (assoc Command
         :decision (s/enum :approve :reject)))

(def Workflow
  {:type :workflow/dynamic
   :handlers [UserId]})

(def States #{::draft ::submitted ::approved ::rejected ::closed})
(def CommandTypes #{::submit ::return #_::accept-license #_::require-license ::request-decision ::decide #_::request-comment #_::comment ::approve ::reject ::close})
(def EventTypes #{:event/submitted :event/returned #_:event/license-required #_:event/license-accepted #_:event/comment-requested #_:event/commented :event/decision-requested :event/decided :event/approved :event/rejected :event/closed})





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
  [application _workflow _event]
  (assoc application :state ::submitted ))

(defmethod apply-event [:event/approved :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state ::approved))

(defmethod apply-event [:event/rejected :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state ::rejected))

(defmethod apply-event [:event/returned :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state ::returned))

(defmethod apply-event [:event/closed :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state ::closed))

(defmethod apply-event [:event/decision-requested :workflow/dynamic]
  [application _workflow event]
  (assoc application :decider (:decider event)))

(defmethod apply-event [:event/decided :workflow/dynamic]
  [application _workflow event]
  (-> application
      (assoc :decision (:decision event))
      (dissoc :decider)))

(defn apply-events [application events]
  (reduce (fn [application event] (apply-event application (:workflow application) event))
          application
          events))




;;; Commands

(defmulti handle-command
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

(defn- handler?
  [application user]
  (contains? (set (:handlers (:workflow application))) user))

(defmethod handle-command ::approve
  [cmd application]
  (cond (not (handler? application (:actor cmd))) {:errors [:unauthorized]}
        (not= ::submitted (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/approved
                        :actor (:actor cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defmethod handle-command ::reject
  [cmd application]
  (cond (not (handler? application (:actor cmd)))  {:errors [:unauthorized]}
        (not= ::submitted (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/rejected
                        :actor (:actor cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defmethod handle-command ::return
  [cmd application]
  (cond (not (handler? application (:actor cmd)))  {:errors [:unauthorized]}
        (not= ::submitted (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/returned
                        :actor (:actor cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defmethod handle-command ::close
  [cmd application]
  (cond (not (handler? application (:actor cmd)))  {:errors [:unauthorized]}
        (not= ::approved (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/closed
                        :actor (:actor cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defmethod handle-command ::request-decision
  [cmd application]
  (cond (not (handler? application (:actor cmd)))  {:errors [:unauthorized]}
        (not= ::submitted (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/decision-requested
                        :actor (:actor cmd)
                        :decider (:decider cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defmethod handle-command ::decide
  [cmd application]
  (cond (not= (:actor cmd) (:decider application)) {:errors [:unauthorized]}
        (not= ::submitted (:state application)) {:errors [[:invalid-state (:state application)]]}
        :else {:success true
               :result {:event :event/decided
                        :actor (:actor cmd)
                        :decision (:decision cmd)
                        :application-id (:application-id cmd)
                        :time (:time cmd)}}))

(defn- apply-command [application cmd]
  (let [result (handle-command cmd application)]
    (assert (:success result) (pr-str result))
    (apply-event application (:workflow application) (getx result :result))))

(defn- apply-commands [application commands]
  (reduce apply-command application commands))





;;; Tests

(deftest test-submit-approve-or-reject
  (let [application {:state ::draft
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}]
    (is (= ::approved (:state (apply-commands application
                                              [{:actor "applicant" :type ::submit}
                                               {:actor "assistant" :type ::approve}]))))
    (is (= ::rejected (:state (apply-commands application
                                              [{:actor "applicant" :type ::submit}
                                               {:actor "assistant" :type ::reject}]))))))

(deftest test-submit-return-submit-approve-close
  (let [application {:state ::draft
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        returned-application (apply-commands application
                                             [{:actor "applicant" :type ::submit}
                                              {:actor "assistant" :type ::return}])
        approved-application (apply-commands returned-application [{:actor "applicant" :type ::submit}
                                                                   {:actor "assistant" :type ::approve}])
        closed-application (apply-command approved-application {:actor "assistant" :type ::close})]
    (is (= ::returned (:state returned-application)))
    (is (= ::approved (:state approved-application)))
    (is (= ::closed (:state closed-application)))))

(deftest test-decision
  (let [application {:state ::submitted
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        requested (apply-command application {:actor "assistant" :decider "deity" :type ::request-decision})
        decided (apply-command requested {:actor "deity" :decision :approved :type ::decide})]
    (is (= {:decider "deity"} (select-keys requested [:decider :decision])))
    (is (= {:decision :approved} (select-keys decided [:decider :decision])))))
