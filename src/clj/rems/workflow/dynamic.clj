(ns rems.workflow.dynamic
  (:require [clojure.test :refer :all]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.util :refer [getx]]
            [schema-refined.core :as r]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;;; Schemas

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

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

(def States
  #{::approved
    ::closed
    ::draft
    ::rejected
    ::submitted})
(def CommandTypes
  #{#_::accept-license
    #_::require-license
    ::add-member
    ::approve
    ::close
    ::comment
    ::decide
    ::reject
    ::request-comment
    ::request-decision
    ::return
    ::save-draft
    ::submit})
(def EventTypes
  #{#_:event/license-accepted
    #_:event/license-required
    :event/approved
    :event/closed
    :event/comment-requested
    :event/commented
    :event/decided
    :event/decision-requested
    :event/draft-saved
    :event/member-added
    :event/rejected
    :event/returned
    :event/submitted})

;; TODO: namespaced keys e.g. :event/type, :event/time, :event/actor, :application/id
;; TODO: add version number to events
(s/defschema EventBase
  {:event (apply s/enum EventTypes)
   :application-id s/Int
   :actor UserId
   :time DateTime})

(s/defschema ApprovedEvent
  (assoc EventBase
         :event (s/eq :event/approved)
         :comment s/Str))
(s/defschema ClosedEvent
  (assoc EventBase
         :event (s/eq :event/closed)
         :comment s/Str))
(s/defschema CommentedEvent
  (assoc EventBase
         :event (s/eq :event/commented)
         :comment s/Str))
(s/defschema CommentRequestedEvent
  (assoc EventBase
         :event (s/eq :event/comment-requested)
         :commenters [s/Str]
         :comment s/Str))
(s/defschema DecidedEvent
  (assoc EventBase
         :event (s/eq :event/decided)
         :decision (s/enum :approved :rejected)
         :comment s/Str))
(s/defschema DecisionRequestedEvent
  (assoc EventBase
         :event (s/eq :event/decision-requested)
         :decider s/Str
         :comment s/Str))
(s/defschema DraftSavedEvent
  (assoc EventBase
         :event (s/eq :event/draft-saved)
         :items {Long s/Str}
         :licenses {Long s/Str}))
(s/defschema MemberAddedEvent
  (assoc EventBase
         :event (s/eq :event/member-added)
         :member s/Str))
(s/defschema RejectedEvent
  (assoc EventBase
         :event (s/eq :event/rejected)
         :comment s/Str))
(s/defschema ReturnedEvent
  (assoc EventBase
         :event (s/eq :event/returned)
         :comment s/Str))
(s/defschema SubmittedEvent
  (assoc EventBase
         :event (s/eq :event/submitted)))

(def event-schemas
  [ApprovedEvent
   ClosedEvent
   CommentedEvent
   CommentRequestedEvent
   DecidedEvent
   DecisionRequestedEvent
   DraftSavedEvent
   MemberAddedEvent
   RejectedEvent
   ReturnedEvent
   SubmittedEvent])

(defn- get-event-type [event-schema]
  (let [event-type (:v (:event event-schema))]
    (assert (keyword? event-type)
            (str "couldn't get the event type from schema " event-schema))
    event-type))

(s/defschema Event
  (apply r/dispatch-on (flatten [:event
                                 (for [schema event-schemas]
                                   [(get-event-type schema) schema])])))

(deftest test-event-schema
  (testing "check specific event schema"
    (is (nil? (s/check SubmittedEvent {:event :event/submitted
                                       :actor "foo"
                                       :application-id 123
                                       :time (DateTime.)}))))
  (testing "check generic event schema"
    (is (nil? (s/check Event
                       {:event :event/submitted
                        :actor "foo"
                        :application-id 123
                        :time (DateTime.)})))
    (is (nil? (s/check Event
                       {:event :event/approved
                        :actor "foo"
                        :application-id 123
                        :time (DateTime.)
                        :comment "foo"}))))
  (testing "missing event specific key"
    (is (= {:comment 'missing-required-key}
           (s/check Event
                    {:event :event/approved
                     :actor "foo"
                     :application-id 123
                     :time (DateTime.)}))))
  (testing "unknown event type"
    ;; TODO: improve error message to show the actual and expected event types
    (is (= "(not (some-matching-condition? a-clojure.lang.PersistentArrayMap))"
           (pr-str (s/check Event
                            {:event :foo
                             :actor "foo"
                             :application-id 123
                             :time (DateTime.)}))))))


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

(defmethod apply-event [:event/draft-saved :workflow/dynamic]
  [application _workflow event]
  (assoc application :form-contents {:items (:items event)
                                     :licenses (:licenses event)}))

(defmethod apply-event [:event/submitted :workflow/dynamic]
  [application _workflow event]
  (assoc application
         :state ::submitted
         :commenters #{}
         :members [(:actor event)]
         :previous-submitted-form-contents (:submitted-form-contents application)
         :submitted-form-contents (:form-contents application)))

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

(defmethod apply-event [:event/comment-requested :workflow/dynamic]
  [application _workflow event]
  (update application :commenters into (:commenters event)))

(defmethod apply-event [:event/commented :workflow/dynamic]
  [application _workflow event]
  ;; we don't store the comments in the state, they're available via
  ;; the event list
  (update application :commenters disj (:actor event)))

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
    {:errors [:forbidden]}))

(defn- handler?
  [application user]
  (contains? (set (:handlers (:workflow application))) user))

(defn- actor-is-not-handler-error
  [application cmd]
  (when-not (handler? application (:actor cmd))
    {:errors [:forbidden]}))

(defn- state-error
  [application & expected-states]
  (when-not (contains? (set expected-states) (:state application))
    {:errors [[:invalid-state (:state application)]]}))

(defn- valid-user-error
  [injections user]
  (cond
    (not (:valid-user? injections)) {:errors [[:missing-injection :valid-user?]]}
    (not ((:valid-user? injections) user)) {:errors [[:t.form.validation/invalid-user user]]}))

(defn- validation-error
  [injections application-id]
  (when-let [errors ((:validate-form injections) application-id)]
    {:errors errors}))

(defmethod handle-command ::save-draft
  [cmd application _injections]
  (or (applicant-error application cmd)
      (state-error application ::draft ::returned)
      {:success true
       :result {:event :event/draft-saved
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)
                :items (:items cmd)
                :licenses (:licenses cmd)}}))

(defmethod handle-command ::submit
  [cmd application injections]
  (or (applicant-error application cmd)
      (state-error application ::draft ::returned)
      (validation-error injections (:application-id cmd))
      {:success true
       :result {:event :event/submitted
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::approve
  [cmd application _injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event :event/approved
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :comment (:comment cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::reject
  [cmd application _injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event :event/rejected
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :comment (:comment cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::return
  [cmd application _injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event :event/returned
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :comment (:comment cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::close
  [cmd application _injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::approved)
      {:success true
       :result {:event :event/closed
                :actor (:actor cmd)
                :application-id (:application-id cmd)
                :comment (:comment cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::request-decision
  [cmd application injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      (valid-user-error injections (:decider cmd))
      {:success true
       :result {:event :event/decision-requested
                :actor (:actor cmd)
                :decider (:decider cmd)
                :application-id (:application-id cmd)
                :comment (:comment cmd)
                :time (:time cmd)}}))

(defmethod handle-command ::decide
  [cmd application _injections]
  (or (when-not (= (:actor cmd) (:decider application))
        {:errors [:forbidden]})
      (state-error application ::submitted)
      (when-not (contains? #{:approved :rejected} (:decision cmd))
        {:errors [[:invalid-decision (:decision cmd)]]})
      {:success true
       :result {:event :event/decided
                :actor (:actor cmd)
                :decision (:decision cmd)
                :application-id (:application-id cmd)
                :comment (:comment cmd)
                :time (:time cmd)}}))

(defn- invalid-users-errors
  "Checks the given users for validity and merges the errors"
  [user-ids injections]
  (apply merge-with into (keep (partial valid-user-error injections) user-ids)))

(defn- must-not-be-empty [cmd key]
  (when-not (seq (get cmd key))
    {:errors [[:must-not-be-empty key]]}))

(defmethod handle-command ::request-comment
  [cmd application injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      (must-not-be-empty cmd :commenters)
      (invalid-users-errors (:commenters cmd) injections)
      {:success true
       :result {:event :event/comment-requested
                :actor (:actor cmd)
                :commenters (:commenters cmd)
                :application-id (:application-id cmd)
                :comment (:comment cmd)
                :time (:time cmd)}}))

(defn- actor-is-not-commenter-error [application cmd]
  (when-not (contains? (:commenters application) (:actor cmd))
    {:errors [:forbidden]}))

(defmethod handle-command ::comment
  [cmd application _injections]
  (or (actor-is-not-commenter-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event :event/commented
                :actor (:actor cmd)
                :comment (:comment cmd)
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
   {:type ::request-comment
    :actor actor
    :commenters ["commenter"]}
   {:type ::comment
    :actor actor
    :comment "comment"}
   {:type ::add-member
    :actor actor
    :member "member"}])

(def ^:private injections-for-possible-commands
  "`possible-commands` are calculated with the expectations that
  - the user is always valid and
  - the validation returns no errors."
  {:valid-user? (constantly true)
   :validate-form (constantly nil)})

(defn possible-commands
  "Calculates which commands should be possible for use in e.g. UI.

  Not every condition is checked exactly so it is in fact a potential set of possible commands only."
  [actor application-state]
  (set
   (map :type
        (remove #(impossible-command? % application-state injections-for-possible-commands)
                (command-candidates actor application-state)))))

(defn assoc-possible-commands [actor application-state]
  (assoc application-state
         :possible-commands (possible-commands actor application-state)))

;;; Tests

(deftest test-save-draft
  (let [injections {:validate-form (constantly nil)}
        application {:state ::draft
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        relevant-application-keys [:state :form-contents :submitted-form-contents :previous-submitted-form-contents]]
    (testing "saves a draft"
      (is (= {:success true
              :result {:event :event/draft-saved
                       :actor "applicant"
                       :application-id 123
                       :time 456
                       :items {1 "foo" 2 "bar"}
                       :licenses {1 "approved" 2 "approved"}}}
             (handle-command {:type ::save-draft
                              :actor "applicant"
                              :application-id 123
                              :time 456
                              :items {1 "foo" 2 "bar"}
                              :licenses {1 "approved" 2 "approved"}}
                             application
                             injections))))
    (testing "only the applicant can save a draft"
      (is (= {:errors [:forbidden]}
             (handle-command {:type ::save-draft
                              :actor "non-applicant"
                              :application-id 123
                              :time 456
                              :items {1 "foo" 2 "bar"}
                              :licenses {1 "approved" 2 "approved"}}
                             application
                             injections)
             (handle-command {:type ::save-draft
                              :actor "assistant"
                              :application-id 123
                              :time 456
                              :items {1 "foo" 2 "bar"}
                              :licenses {1 "approved" 2 "approved"}}
                             application
                             injections))))
    (testing "draft can be updated multiple times"
      (is (= {:state :rems.workflow.dynamic/draft
              :form-contents {:items {1 "updated"}
                              :licenses {2 "updated"}}}
             (-> (apply-commands application
                                 [{:actor "applicant" :type ::save-draft :items {1 "original"} :licenses {2 "original"}}
                                  {:actor "applicant" :type ::save-draft :items {1 "updated"} :licenses {2 "updated"}}]
                                 injections)
                 (select-keys relevant-application-keys)))))
    (testing "draft cannot be updated after submitting"
      (let [application (apply-commands application
                                        [{:actor "applicant" :type ::save-draft :items {1 "original"} :licenses {2 "original"}}
                                         {:actor "applicant" :type ::submit}]
                                        injections)]
        (is (= {:errors [[:invalid-state ::submitted]]}
               (handle-command {:type ::save-draft
                                :actor "applicant"
                                :items {1 "updated"} :licenses {2 "updated"}}
                               application
                               injections)))))
    (testing "draft can be updated after returning it to applicant"
      (is (= {:state ::returned
              :form-contents {:items {1 "updated"}
                              :licenses {2 "updated"}}
              :submitted-form-contents {:items {1 "original"}
                                        :licenses {2 "original"}}
              :previous-submitted-form-contents nil}
             (-> (apply-commands application
                                 [{:actor "applicant" :type ::save-draft :items {1 "original"} :licenses {2 "original"}}
                                  {:actor "applicant" :type ::submit}
                                  {:actor "assistant" :type ::return}
                                  {:actor "applicant" :type ::save-draft :items {1 "updated"} :licenses {2 "updated"}}]
                                 injections)
                 (select-keys relevant-application-keys)))))
    (testing "resubmitting remembers the previous and current application"
      (is (= {:state ::submitted
              :form-contents {:items {1 "updated"}
                              :licenses {2 "updated"}}
              :submitted-form-contents {:items {1 "updated"}
                                        :licenses {2 "updated"}}
              :previous-submitted-form-contents {:items {1 "original"}
                                                 :licenses {2 "original"}}}
             (-> (apply-commands application
                                 [{:actor "applicant" :type ::save-draft :items {1 "original"} :licenses {2 "original"}}
                                  {:actor "applicant" :type ::submit}
                                  {:actor "assistant" :type ::return}
                                  {:actor "applicant" :type ::save-draft :items {1 "updated"} :licenses {2 "updated"}}
                                  {:actor "applicant" :type ::submit}]
                                 injections)
                 (select-keys relevant-application-keys)))))))

(deftest test-submit-approve-or-reject
  (let [injections {:validate-form (constantly nil)}
        expected-errors [{:key :t.form.validation/required}]
        fail-injections {:validate-form (constantly expected-errors)}
        application {:state ::draft
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}]
    (testing "only applicant can submit"
      (is (= {:errors [:forbidden]}
             (handle-command {:actor "not-applicant" :type ::submit} application injections))))
    (testing "can only submit valid form"
      (is (= {:errors expected-errors}
             (handle-command {:actor "applicant" :type ::submit} application fail-injections))))
    (let [submitted (apply-command application {:actor "applicant" :type ::submit} injections)]
      (testing "cannot submit twice"
        (is (= {:errors [[:invalid-state ::submitted]]}
               (handle-command {:actor "applicant" :type ::submit} submitted injections))))
      (testing "submitter is member"
        (is (= ["applicant"] (:members submitted))))
      (testing "approving"
        (is (= ::approved (:state (apply-command submitted
                                                 {:actor "assistant" :type ::approve}
                                                 injections)))))
      (testing "rejecting"
        (is (= ::rejected (:state (apply-command submitted
                                                 {:actor "assistant" :type ::reject}
                                                 injections))))))))

(deftest test-submit-return-submit-approve-close
  (let [injections {:validate-form (constantly nil)}
        application {:state ::draft
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        returned-application (apply-commands application
                                             [{:actor "applicant" :type ::submit}
                                              {:actor "assistant" :type ::return}]
                                             injections)
        approved-application (apply-commands returned-application [{:actor "applicant" :type ::submit}
                                                                   {:actor "assistant" :type ::approve}]
                                             injections)
        closed-application (apply-command approved-application {:actor "assistant" :type ::close}
                                          injections)]
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
      (is (= {:errors [[:t.form.validation/invalid-user "deity2"]]}
             (handle-command {:actor "assistant" :decider "deity2" :type ::request-decision}
                             application
                             injections))))
    (testing "deciding before ::request-decision should fail"
      (is (= {:errors [:forbidden]}
             (handle-command {:actor "deity" :decision :approved :type ::decide}
                             application
                             injections))))
    (let [requested (apply-command application {:actor "assistant" :decider "deity" :type ::request-decision} injections)]
      (testing "request decision succesfully"
        (is (= {:decider "deity"} (select-keys requested [:decider :decision]))))
      (testing "only the requested user can decide"
        (is (= {:errors [:forbidden]}
               (handle-command {:actor "deity2" :decision :approved :type ::decide}
                               requested
                               injections))))
      (let [approved (apply-command requested {:actor "deity" :decision :approved :type ::decide} injections)]
        (testing "succesfully approved"
          (is (= {:decision :approved} (select-keys approved [:decider :decision]))))
        (testing "cannot approve twice"
          (is (= {:errors [:forbidden]}
                 (handle-command {:actor "deity" :decision :approved :type ::decide}
                                 approved
                                 injections)))))
      (let [rejected (apply-command requested {:actor "deity" :decision :rejected :type ::decide} injections)]
        (testing "successfully rejected"
          (is (= {:decision :rejected} (select-keys rejected [:decider :decision]))))
        (testing "can not reject twice"
          (is (= {:errors [:forbidden]}
                 (handle-command {:actor "deity" :decision :rejected :type ::decide}
                                 rejected
                                 injections)))))
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
      (is (= {:errors [:forbidden]}
             (handle-command {:type ::add-member :actor "member1" :member "member1"}
                             application
                             injections))))
    (testing "only valid users can be added"
      (is (= {:errors [[:t.form.validation/invalid-user "member3"]]}
             (handle-command {:type ::add-member :actor "applicant" :member "member3"}
                             application
                             injections))))
    (testing "can't add members to approved application"
      (is (= {:errors [[:invalid-state ::approved]]}
             (handle-command {:type ::add-member :actor "applicant" :member "member1"}
                             (assoc application :state ::approved)
                             injections))))))

(deftest test-comment
  (let [application {:state ::submitted
                     :applicantuserid "applicant"
                     :commenters #{}
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        injections {:valid-user? #{"commenter" "commenter2" "commenter3"}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [[:missing-injection :valid-user?]]}
             (handle-command {:actor "assistant" :commenters ["commenter"] :type ::request-comment}
                             application
                             {}))))
    (testing "commenters must not be empty"
      (is (= {:errors [[:must-not-be-empty :commenters]]}
             (handle-command {:actor "assistant" :commenters [] :type ::request-comment}
                             application
                             {}))))
    (testing "commenters must be a valid users"
      (is (= {:errors [[:t.form.validation/invalid-user "invaliduser"] [:t.form.validation/invalid-user "invaliduser2"]]}
             (handle-command {:actor "assistant" :commenters ["invaliduser" "commenter" "invaliduser2"] :type ::request-comment}
                             application
                             injections))))
    (testing "commenting before ::request-comment should fail"
      (is (= {:errors [:forbidden]}
             (handle-command {:actor "commenter" :decision :approved :type ::comment}
                             application
                             injections))))
    (let [requested (apply-commands application
                                    [{:actor "assistant" :commenters ["commenter"] :type ::request-comment}
                                     {:actor "assistant" :commenters ["commenter2"] :type ::request-comment}]
                                    injections)]
      (testing "request comment succesfully"
        (is (= #{"commenter2" "commenter"} (:commenters requested))))
      (testing "only the requested commenter can comment"
        (is (= {:errors [:forbidden]}
               (handle-command {:actor "commenter3" :comment "..." :type ::comment}
                               requested
                               injections))))
      (let [commented (apply-command requested {:actor "commenter" :comment "..." :type ::comment} injections)]
        (testing "succesfully commented"
          (is (= #{"commenter2"} (:commenters commented))))
        (testing "cannot comment twice"
          (is (= {:errors [:forbidden]}
                 (handle-command {:actor "commenter" :comment "..." :type ::comment}
                                 commented
                                 injections))))
        (testing "other commenter can also comment"
          (is (= #{} (:commenters (apply-command commented
                                                 {:actor "commenter2" :comment "..." :type ::comment}
                                                 injections)))))))))

(deftest test-possible-commands
  (let [draft {:state ::draft
               :applicantuserid "applicant"
               :workflow {:type :workflow/dynamic
                          :handlers ["assistant"]}}]
    (testing "draft"
      (is (= #{::submit ::add-member}
             (possible-commands "applicant" draft)))
      (is (= #{}
             (possible-commands "assistant" draft)))
      (is (= #{}
             (possible-commands "somebody else" draft))))
    (let [submitted (apply-events draft [{:event :event/submitted
                                          :actor "applicant"}])]
      (testing "submitted"
        (is (= #{::add-member}
               (possible-commands "applicant" submitted)))
        (is (= #{::approve ::reject ::return ::request-decision ::request-comment}
               (possible-commands "assistant" submitted)))
        (is (= #{}
               (possible-commands "somebody else" submitted))))
      (let [requested (apply-events submitted [{:event :event/comment-requested
                                                :actor "assistant"
                                                :commenters ["commenter"]}])]
        (testing "comment requested"
          (is (= #{::add-member}
                 (possible-commands "applicant" requested)))
          (is (= #{::approve ::reject ::return ::request-decision ::request-comment}
                 (possible-commands "assistant" requested)))
          (is (= #{::comment}
                 (possible-commands "commenter" requested))))
        (let [commented (apply-events requested [{:event :event/commented
                                                  :actor "commenter"
                                                  :comment "..."}])]
          (testing "comment given"
            (is (= #{::approve ::reject ::return ::request-decision ::request-comment}
                   (possible-commands "assistant" commented)))
            (is (= #{}
                   (possible-commands "commenter" commented))))))
      (let [requested (apply-events submitted [{:event :event/decision-requested
                                                :actor "assistant"
                                                :decider "decider"}])]
        (testing "decision requested"
          (is (= #{::add-member}
                 (possible-commands "applicant" requested)))
          (is (= #{::approve ::reject ::return ::request-decision ::request-comment}
                 (possible-commands "assistant" requested)))
          (is (= #{::decide}
                 (possible-commands "decider" requested)))))
      (let [rejected (apply-events submitted [{:event :event/rejected
                                               :actor "assistant"}])]
        (testing "rejected"
          (is (= #{}
                 (possible-commands "applicant" rejected)))
          (is (= #{}
                 (possible-commands "assistant" rejected)))
          (is (= #{}
                 (possible-commands "somebody else" rejected)))))
      (let [approved (apply-events submitted [{:event :event/approved
                                               :actor "assistant"}])]
        (testing "approved"
          (is (= #{}
                 (possible-commands "applicant" approved)))
          (is (= #{::close}
                 (possible-commands "assistant" approved)))
          (is (= #{}
                 (possible-commands "somebody else" approved))))
        (testing "closed"
          (let [closed (apply-events approved [{:event :event/closed
                                                :actor "assistant"}])]
            (is (= #{}
                   (possible-commands "applicant" closed)))
            (is (= #{}
                   (possible-commands "assistant" closed)))
            (is (= #{}
                   (possible-commands "somebody else" closed)))))))))
