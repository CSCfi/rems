(ns rems.workflow.dynamic
  (:require [clojure.test :refer :all]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.util :refer [getx]]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;;; Schemas

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema Event
  {:actor UserId
   :application-id Long
   :event s/Keyword
   :comment (s/maybe s/Str)
   :time DateTime})

(s/defschema Command
  {:type s/Keyword
   :actor UserId
   :application-id Long
   :time DateTime})

(s/defschema RequestDecisionCommand
  (assoc Command
         :decider UserId))

(s/defschema DecisionCommand
  (assoc Command
         :decision (s/enum :approve :reject)))

(s/defschema Workflow
  {:type :workflow/dynamic
   :handlers [UserId]})

(def States #{::draft ::submitted ::approved ::rejected ::closed})
(def CommandTypes #{::submit ::return #_::accept-license #_::require-license ::request-decision ::decide #_::request-comment #_::comment ::approve ::reject ::close ::add-member})
(def EventTypes #{:event/submitted :event/returned #_:event/license-required #_:event/license-accepted #_:event/comment-requested #_:event/commented :event/decision-requested :event/decided :event/approved :event/rejected :event/closed :event/member-added})





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
  [application _workflow event]
  (assoc application
         :state ::submitted
         :members [(:actor event)]))

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

(defmethod apply-event [:event/member-added :workflow/dynamic]
  [application _workflow event]
  (update application :members #(vec (conj % (:member event)))))

(defn apply-events [application events]
  (reduce (fn [application event] (apply-event application (:workflow application) event))
          application
          events))

;;; Commands

(defmulti handle-command
  "Handles a command by an event."
  (fn [cmd _application _injections] (:type cmd)))

(defn get-command-types
  "Fetch sequence of supported command names."
  []
  (keys (methods handle-command)))

(deftest test-all-command-types-handled
  (is (= CommandTypes (set (get-command-types)))))

(defn impossible-command? [cmd application injections]
  (let [result (handle-command cmd application injections)]
    (when-not (:success result)
      result)))

(defn- applicant-error
  [application cmd]
  (when-not (= (:actor cmd) (:applicantuserid application))
    {:errors [:unauthorized]}))

(defn- handler?
  [application user]
  (contains? (set (:handlers (:workflow application))) user))

(defn- handler-error
  [application cmd]
  (when-not (handler? application (:actor cmd))
    {:errors [:unauthorized]}))

(defn- state-error
  [application & states]
  (when-not (contains? (set states) (:state application))
    {:errors [[:invalid-state (:state application)]]}))

(defn- valid-user-error
  [injections user]
  (cond
    (not (:valid-user? injections)) {:errors [[:missing-injection :valid-user?]]}
    (not ((:valid-user? injections) user)) {:errors [[:invalid-user user]]}))

(defmethod handle-command ::submit
  [cmd application _injections]
  (or (applicant-error application cmd)
      (state-error application ::draft ::returned)
      {:success true
       :result {:event :event/submitted
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::approve
  [cmd application _injections]
  (or (handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event :event/approved
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::reject
  [cmd application _injections]
  (or (handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event :event/rejected
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::return
  [cmd application _injections]
  (or (handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event :event/returned
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::close
  [cmd application _injections]
  (or (handler-error application cmd)
      (state-error application ::approved)
      {:success true
       :result {:event :event/closed
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::request-decision
  [cmd application injections]
  (or (handler-error application cmd)
      (state-error application ::submitted)
      (valid-user-error injections (:decider cmd))
      {:success true
       :result {:event :event/decision-requested
                :actor (:actor cmd)
                :decider (:decider cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::decide
  [cmd application _injections]
  (or (when-not (= (:actor cmd) (:decider application))
        {:errors [:unauthorized]})
      (state-error application ::submitted)
      (when-not (contains? #{:approved :rejected} (:decision cmd))
        {:errors [[:invalid-decision (:decision cmd)]]})
      {:success true
       :result {:event :event/decided
                :actor (:actor cmd)
                :decision (:decision cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::add-member
  [cmd application injections]
  ;; TODO should handler be able to add members?
  (or (applicant-error application cmd)
      (state-error application ::draft ::submitted) ;; TODO which states?
      (valid-user-error injections (:member cmd))
      {:success true
       :result {:event :event/member-added
                :actor (:actor cmd)
                :member (:member cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defn- apply-command
  ([application cmd]
   (apply-command application cmd nil))
  ([application cmd injections]
   (let [result (handle-command cmd application injections)]
     (assert (:success result) (pr-str result))
     (apply-event application (:workflow application) (getx result :result)))))

(defn- apply-commands
  ([application commands]
   (apply-commands application commands nil))
  ([application commands injections]
   (reduce (fn [app cmd] (apply-command app cmd injections))
           application commands)))


;;; Possible commands

(defn- command-candidates [actor application-state]
  ;; NB! not setting :time or :application-id here since we don't
  ;; validate them
  [{:type ::submit
    :actor actor}
   {:type ::approve
    :actor actor}
   {:type ::reject
    :actor actor}
   {:type ::return
    :actor actor}
   {:type ::close
    :actor actor}
   {:type ::request-decision
    :actor actor
    :decider "decider"}
   {:type ::decide
    :actor actor
    :decision :approved}
   {:type ::add-member
    :actor actor
    :member "member"}])

(def ^:private injections-for-possible-commands
  {:valid-user? (constantly true)})

(defn possible-commands [actor application-state]
  (set
   (map :type
        (remove #(impossible-command? % application-state injections-for-possible-commands)
                (command-candidates actor application-state)))))

;;; Tests

(deftest test-submit-approve-or-reject
  (let [application {:state ::draft
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        submitted (apply-command application {:actor "applicant" :type ::submit})]
    (testing "submitter is member"
      (is (= ["applicant"] (:members submitted))))
    (testing "approving"
      (is (= ::approved (:state (apply-command submitted
                                               {:actor "assistant" :type ::approve})))))
    (testing "rejecting"
      (is (= ::rejected (:state (apply-command submitted
                                               {:actor "assistant" :type ::reject})))))))

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
        injections {:valid-user? #{"deity"}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [[:missing-injection :valid-user?]]}
             (handle-command {:actor "assistant" :decider "deity" :type ::request-decision}
                             application
                             {}))))
    (testing "decider must be a valid user"
        (is (= {:errors [[:invalid-user "deity2"]]}
               (handle-command {:actor "assistant" :decider "deity2" :type ::request-decision}
                               application
                               injections))))
    (testing "deciding before ::request-decision should fail"
      (is (= {:errors [:unauthorized]}
             (handle-command {:actor "deity" :decision :approved :type ::decide}
                             application
                             injections))))
    (let [requested (apply-command application {:actor "assistant" :decider "deity" :type ::request-decision} injections)]
      (testing "request decision succesfully"
        (is (= {:decider "deity"} (select-keys requested [:decider :decision]))))
      (testing "only the requested user can decide"
        (is (= {:errors [:unauthorized]}
               (handle-command {:actor "deity2" :decision :approved :type ::decide}
                               requested
                               injections))))
      (testing "succesfully approved"
        (is (= {:decision :approved} (select-keys
                                      (apply-command requested {:actor "deity" :decision :approved :type ::decide} injections)
                                      [:decider :decision]))))
      (testing "successfully rejected"
        (is (= {:decision :rejected} (select-keys
                                      (apply-command requested {:actor "deity" :decision :rejected :type ::decide} injections)
                                      [:decider :decision]))))
      (testing "other decisions are not possible"
            (is (= {:errors [[:invalid-decision :foobar]]}
                   (handle-command {:actor "deity" :decision :foobar :type ::decide}
                                   requested
                                   injections)))))))

(deftest test-add-member
  (let [application {:state ::submitted
                     :members ["applicant" "somebody"]
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic}}
        injections {:valid-user? #{"member1" "member2"}}]
    (testing "add two members"
      (is (= ["applicant" "somebody" "member1" "member2"]
             (:members
              (apply-commands application
                              [{:type ::add-member :actor "applicant" :member "member1"}
                               {:type ::add-member :actor "applicant" :member "member2"}]
                              injections)))))
    (testing "only applicant can add members"
      (is (= {:errors [:unauthorized]}
             (handle-command {:type ::add-member :actor "member1" :member "member1"}
                             application
                             injections))))
    (testing "only valid users can be added"
      (is (= {:errors [[:invalid-user "member3"]]}
             (handle-command {:type ::add-member :actor "applicant" :member "member3"}
                             application
                             injections))))
    (testing "can't add members to approved application"
      (is (= {:errors [[:invalid-state ::approved]]}
             (handle-command {:type ::add-member :actor "applicant" :member "member1"}
                             (assoc application :state ::approved)
                             injections))))))

(deftest test-possible-commands
  (let [base {:applicantuserid "applicant"
              :workflow {:type :workflow/dynamic
                         :handlers ["assistant"]}}]
    (testing "draft"
      (let [draft (assoc base :state ::draft)]
      (is (= #{::submit ::add-member}
             (possible-commands "applicant" draft)))
      (is (= #{}
             (possible-commands "assistant" draft)))
      (is (= #{}
             (possible-commands "somebody else" draft)))))
    (testing "submitted"
      (let [submitted (assoc base :state ::submitted)]
        (is (= #{::add-member}
               (possible-commands "applicant" submitted)))
        (is (= #{::approve ::reject ::return ::request-decision}
               (possible-commands "assistant" submitted)))
        (is (= #{}
               (possible-commands "somebody else" submitted)))))
    (testing "decision requested"
      (let [requested (assoc base
                             :state ::submitted
                             :decider "decider")]
        (is (= #{::add-member}
               (possible-commands "applicant" requested)))
        (is (= #{::approve ::reject ::return ::request-decision}
               (possible-commands "assistant" requested)))
        (is (= #{::decide}
               (possible-commands "decider" requested)))))
    (testing "approved"
      (let [approved (assoc base :state ::approved)]
        (is (= #{}
               (possible-commands "applicant" approved)))
        (is (= #{::close}
               (possible-commands "assistant" approved)))
        (is (= #{}
               (possible-commands "somebody else" approved)))))
    (testing "rejected"
      (let [rejected (assoc base :state ::rejected)]
        (is (= #{}
               (possible-commands "applicant" rejected)))
        (is (= #{}
               (possible-commands "assistant" rejected)))
        (is (= #{}
               (possible-commands "somebody else" rejected)))))
    (testing "closed"
      (let [closed (assoc base :state ::closed)]
        (is (= #{}
               (possible-commands "applicant" closed)))
        (is (= #{}
               (possible-commands "assistant" closed)))
        (is (= #{}
               (possible-commands "somebody else" closed)))))))
