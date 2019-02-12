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
    ::returned
    ::submitted
    #_::withdrawn}) ; TODO withdraw support?

(def CommandTypes
  #{#_::accept-license
    #_::require-license
    ::add-member
    ::invite-member
    ::approve
    ::close
    ::comment
    ::decide
    ::reject
    ::request-comment
    ::request-decision
    ::remove-member
    ::return
    ::save-draft
    ::submit
    ::uninvite-member
    #_::withdraw})

;; TODO: namespaced keys e.g. :event/type, :event/time, :event/actor, :application/id
;; TODO: add version number to events
(s/defschema EventBase
  {(s/optional-key :event/id) s/Int
   :event/type s/Keyword
   :event/time DateTime
   :event/actor UserId
   :application/id s/Int})

(s/defschema ApprovedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/approved)
         :application/comment s/Str))
(s/defschema ClosedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/closed)
         :application/comment s/Str))
(s/defschema CommentedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/commented)
         :application/comment s/Str))
(s/defschema CommentRequestedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/comment-requested)
         :application/commenters [s/Str]
         :application/comment s/Str))
(s/defschema CreatedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/created)
         :application/resources [{:catalogue-item/id s/Int
                                  :resource/ext-id s/Str}]
         :application/licenses [{:license/id s/Int}]
         :form/id s/Int
         :workflow/id s/Int
         :workflow/type s/Keyword
         ;; workflow-specific data
         (s/optional-key :workflow.dynamic/handlers) #{s/Str}))
(s/defschema DecidedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/decided)
         :application/decision (s/enum :approved :rejected)
         :application/comment s/Str))
(s/defschema DecisionRequestedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/decision-requested)
         :application/decider s/Str
         :application/comment s/Str))
(s/defschema DraftSavedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/draft-saved)
         :application/field-values {s/Int s/Str}
         :application/accepted-licenses #{s/Int}))
(s/defschema MemberAddedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/member-added)
         :application/member {:userid s/Str}))
(s/defschema MemberInvitedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/member-invited)
         :application/member {:name s/Str
                              :email s/Str}))
(s/defschema MemberRemovedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/member-removed)
         :application/member {:userid s/Str}
         :application/comment s/Str))
(s/defschema MemberUninvitedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/member-uninvited)
         :application/member {:name s/Str
                              :email s/Str}
         :application/comment s/Str))
(s/defschema RejectedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/rejected)
         :application/comment s/Str))
(s/defschema ReturnedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/returned)
         :application/comment s/Str))
(s/defschema SubmittedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/submitted)))

;; TODO: license-accepted & license-required events
(def event-schemas
  {:application.event/approved ApprovedEvent
   :application.event/closed ClosedEvent
   :application.event/commented CommentedEvent
   :application.event/comment-requested CommentRequestedEvent
   :application.event/created CreatedEvent
   :application.event/decided DecidedEvent
   :application.event/decision-requested DecisionRequestedEvent
   :application.event/draft-saved DraftSavedEvent
   :application.event/member-added MemberAddedEvent
   :application.event/member-invited MemberInvitedEvent
   :application.event/member-removed MemberRemovedEvent
   :application.event/member-uninvited MemberUninvitedEvent
   :application.event/rejected RejectedEvent
   :application.event/returned ReturnedEvent
   :application.event/submitted SubmittedEvent})

(s/defschema Event
  (apply r/dispatch-on (flatten [:event/type (seq event-schemas)])))

(deftest test-event-schema
  (testing "check specific event schema"
    (is (nil? (s/check SubmittedEvent {:event/type :application.event/submitted
                                       :event/time (DateTime.)
                                       :event/actor "foo"
                                       :application/id 123}))))
  (testing "check generic event schema"
    (is (nil? (s/check Event
                       {:event/type :application.event/submitted
                        :event/time (DateTime.)
                        :event/actor "foo"
                        :application/id 123})))
    (is (nil? (s/check Event
                       {:event/type :application.event/approved
                        :event/time (DateTime.)
                        :event/actor "foo"
                        :application/id 123
                        :application/comment "foo"}))))
  (testing "missing event specific key"
    (is (= {:application/comment 'missing-required-key}
           (s/check Event
                    {:event/type :application.event/approved
                     :event/time (DateTime.)
                     :event/actor "foo"
                     :application/id 123}))))
  (testing "unknown event type"
    ;; TODO: improve error message to show the actual and expected event types
    (is (= "(not (some-matching-condition? a-clojure.lang.PersistentArrayMap))"
           (pr-str (s/check Event
                            {:event/type :foo
                             :event/time (DateTime.)
                             :event/actor "foo"
                             :application/id 123}))))))


;;; Events

(defmulti ^:private apply-event
  "Applies an event to an application state."
  ;; dispatch by event type
  ;; TODO: the workflow parameter could be removed; this method is the only one to use it and it's included in application
  (fn [_application workflow event] [(:event/type event) (or (:type workflow)
                                                             (:workflow/type event))]))

(defn get-event-types
  "Fetch sequence of supported event names."
  []
  (map first (keys (methods apply-event))))

(deftest test-all-event-types-handled
  (is (= (set (keys event-schemas))
         (set (get-event-types)))))

(defmethod apply-event [:application.event/created :workflow/dynamic]
  [application _workflow event]
  (assoc application
         :state ::draft
         :applicantuserid (:event/actor event)
         :workflow {:type (:workflow/type event)
                    :handlers (vec (:workflow.dynamic/handlers event))}))

(defmethod apply-event [:application.event/draft-saved :workflow/dynamic]
  [application _workflow event]
  (assoc application :form-contents {:items (:application/field-values event)
                                     :licenses (->> (:application/accepted-licenses event)
                                                    (map (fn [id] [id "approved"]))
                                                    (into {}))}))

(defmethod apply-event [:application.event/submitted :workflow/dynamic]
  [application _workflow event]
  (assoc application
         :state ::submitted
         :commenters #{}
         :members [{:userid (:event/actor event)}]
         :previous-submitted-form-contents (:submitted-form-contents application)
         :submitted-form-contents (:form-contents application)))

(defmethod apply-event [:application.event/approved :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state ::approved))

(defmethod apply-event [:application.event/rejected :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state ::rejected))

(defmethod apply-event [:application.event/returned :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state ::returned))

(defmethod apply-event [:application.event/closed :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state ::closed))

(defmethod apply-event [:application.event/decision-requested :workflow/dynamic]
  [application _workflow event]
  (assoc application :decider (:application/decider event)))

(defmethod apply-event [:application.event/decided :workflow/dynamic]
  [application _workflow event]
  (-> application
      (assoc :decision (:application/decision event))
      (dissoc :decider)))

(defmethod apply-event [:application.event/comment-requested :workflow/dynamic]
  [application _workflow event]
  (update application :commenters into (:application/commenters event)))

(defmethod apply-event [:application.event/commented :workflow/dynamic]
  [application _workflow event]
  ;; we don't store the comments in the state, they're available via
  ;; the event list
  (update application :commenters disj (:event/actor event)))

(defmethod apply-event [:application.event/member-added :workflow/dynamic]
  [application _workflow event]
  (update application :members #(vec (conj % (:application/member event)))))

(defmethod apply-event [:application.event/member-invited :workflow/dynamic]
  [application _workflow event]
  (update application :invited-members #(vec (conj % (:application/member event)))))

(defmethod apply-event [:application.event/member-removed :workflow/dynamic]
  [application _workflow event]
  (update application :members #(vec (remove #{(:application/member event)} %))))

(defmethod apply-event [:application.event/member-uninvited :workflow/dynamic]
  [application _workflow event]
  (update application :invited-members #(vec (remove #{(:application/member event)} %))))

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
    {:errors [{:type :forbidden}]}))

(defn- handler?
  [application user]
  (contains? (set (:handlers (:workflow application))) user))

(defn- actor-is-not-handler-error
  [application cmd]
  (when-not (handler? application (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defn- actor-is-not-handler-or-applicant-error
  [application cmd]
  (when-not (or (handler? application (:actor cmd))
                (= (:applicantuserid application) (:actor cmd)))
    {:errors [:forbidden]}))

(defn- state-error
  [application & expected-states]
  (when-not (contains? (set expected-states) (:state application))
    {:errors [{:type :invalid-state :state (:state application)}]}))

(defn- valid-user-error
  [injections user]
  (cond
    (not (:valid-user? injections)) {:errors [{:type :missing-injection :injection :valid-user?}]}
    (not ((:valid-user? injections) user)) {:errors [{:type :t.form.validation/invalid-user :userid user}]}))

(defn- validation-error
  [injections application-id]
  (when-let [errors ((:validate-form injections) application-id)]
    {:errors errors}))

(defmethod handle-command ::save-draft
  [cmd application _injections]
  (or (applicant-error application cmd)
      (state-error application ::draft ::returned)
      {:success true
       :result {:event/type :application.event/draft-saved
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/field-values (:items cmd)
                :application/accepted-licenses (->> (:licenses cmd)
                                                    (filter #(= "approved" (second %)))
                                                    (map first)
                                                    set)}}))

(defmethod handle-command ::submit
  [cmd application injections]
  (or (applicant-error application cmd)
      (state-error application ::draft ::returned)
      (validation-error injections (:application-id cmd))
      {:success true
       :result {:event/type :application.event/submitted
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)}}))

(defmethod handle-command ::approve
  [cmd application _injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event/type :application.event/approved
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/comment (:comment cmd)}}))

(defmethod handle-command ::reject
  [cmd application _injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event/type :application.event/rejected
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/comment (:comment cmd)}}))

(defmethod handle-command ::return
  [cmd application _injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event/type :application.event/returned
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/comment (:comment cmd)}}))

(defmethod handle-command ::close
  [cmd application _injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::approved)
      {:success true
       :result {:event/type :application.event/closed
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/comment (:comment cmd)}}))

(defmethod handle-command ::request-decision
  [cmd application injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      (valid-user-error injections (:decider cmd))
      {:success true
       :result {:event/type :application.event/decision-requested
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/decider (:decider cmd)
                :application/comment (:comment cmd)}}))

(defmethod handle-command ::decide
  [cmd application _injections]
  (or (when-not (= (:actor cmd) (:decider application))
        {:errors [{:type :forbidden}]})
      (state-error application ::submitted)
      (when-not (contains? #{:approved :rejected} (:decision cmd))
        {:errors [{:type :invalid-decision :decision (:decision cmd)}]})
      {:success true
       :result {:event/type :application.event/decided
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/decision (:decision cmd)
                :application/comment (:comment cmd)}}))

(defn- invalid-users-errors
  "Checks the given users for validity and merges the errors"
  [user-ids injections]
  (apply merge-with into (keep (partial valid-user-error injections) user-ids)))

(defn- must-not-be-empty [cmd key]
  (when-not (seq (get cmd key))
    {:errors [{:type :must-not-be-empty :key key}]}))

(defmethod handle-command ::request-comment
  [cmd application injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted)
      (must-not-be-empty cmd :commenters)
      (invalid-users-errors (:commenters cmd) injections)
      {:success true
       :result {:event/type :application.event/comment-requested
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/commenters (:commenters cmd)
                :application/comment (:comment cmd)}}))

(defn- actor-is-not-commenter-error [application cmd]
  (when-not (contains? (:commenters application) (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod handle-command ::comment
  [cmd application _injections]
  (or (actor-is-not-commenter-error application cmd)
      (state-error application ::submitted)
      {:success true
       :result {:event/type :application.event/commented
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :application/comment (:comment cmd)}}))

(defmethod handle-command ::add-member
  [cmd application injections]
  (or (actor-is-not-handler-error application cmd)
      (state-error application ::submitted ::approved)
      (valid-user-error injections (:userid (:member cmd)))
      {:success true
       :result {:event/type :application.event/member-added
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/member (:member cmd)
                :application/id (:application-id cmd)}}))

(defmethod handle-command ::invite-member
  [cmd application injections]
  (let [success-result {:success true
                        :result {:event/type :application.event/member-invited
                                 :event/time (:time cmd)
                                 :event/actor (:actor cmd)
                                 :application/member (:member cmd)
                                 :application/id (:application-id cmd)}}]
    (if (= (:actor cmd) (:applicantuserid application))
      (or (applicant-error application cmd)
          (state-error application ::draft #_::withdrawn ::returned)
          success-result)
      (or (actor-is-not-handler-error application cmd)
          (state-error application ::submitted ::approved)
          success-result))))

(defmethod handle-command ::remove-member
  [cmd application injections]
  (or (actor-is-not-handler-or-applicant-error application cmd)
      (when-not (contains? (set (map :userid (:members application)))
                           (:userid (:member cmd)))
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      {:success true
       :result {:event/type :application.event/member-removed
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/member (:member cmd)
                :application/id (:application-id cmd)
                :application/comment (:comment cmd)}}))

(defmethod handle-command ::uninvite-member
  [cmd application injections]
  (or (actor-is-not-handler-or-applicant-error application cmd)
      (when-not (contains? (set (map (juxt :name :email) (:invited-members application)))
                           [(:name (:member cmd))
                            (:email (:member cmd))])
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      {:success true
       :result {:event/type :application.event/member-uninvited
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/member (:member cmd)
                :application/id (:application-id cmd)
                :application/comment (:comment cmd)}}))

(defn- actor-is-not-handler-or-applicant-error
  [application cmd]
  (when-not (or (handler? application (:actor cmd))
                (= (:applicantuserid application) (:actor cmd)))
    {:errors [:forbidden]}))

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
  (->>
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
    :member {:userid "member"}}
   {:type ::invite-member
    :actor actor
    :member {:name "name"
             :email "email@address.org"}}
   (let [members (->> application-state
                      :members
                      (remove (comp #{(:applicantuserid application-state)} :userid)))]
     (when (seq members)
       {:type ::remove-member
        :actor actor
        :member (first members)
        :comment "comment"}))
   (let [invited-members (:invited-members application-state)]
     (when (seq invited-members)
       {:type ::uninvite-member
        :actor actor
        :member (first invited-members)
        :comment "comment"}))]
   (remove nil?)))

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
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}])
        relevant-application-keys [:state :form-contents :submitted-form-contents :previous-submitted-form-contents]]
    (testing "saves a draft"
      (is (= {:success true
              :result {:event/type :application.event/draft-saved
                       :event/time 456
                       :event/actor "applicant"
                       :application/id 123
                       :application/field-values {1 "foo" 2 "bar"}
                       :application/accepted-licenses #{1 2}}}
             (handle-command {:type ::save-draft
                              :time 456
                              :actor "applicant"
                              :application-id 123
                              :items {1 "foo" 2 "bar"}
                              :licenses {1 "approved" 2 "approved"}}
                             application
                             injections))))
    (testing "only the applicant can save a draft"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:type ::save-draft
                              :time 456
                              :actor "non-applicant"
                              :application-id 123
                              :items {1 "foo" 2 "bar"}
                              :licenses {1 "approved" 2 "approved"}}
                             application
                             injections)
             (handle-command {:type ::save-draft
                              :time 456
                              :actor "assistant"
                              :application-id 123
                              :items {1 "foo" 2 "bar"}
                              :licenses {1 "approved" 2 "approved"}}
                             application
                             injections))))
    (testing "draft can be updated multiple times"
      (is (= {:state :rems.workflow.dynamic/draft
              :form-contents {:items {1 "updated"}
                              :licenses {3 "approved"}}}
             (-> (apply-commands application
                                 [{:actor "applicant" :type ::save-draft :items {1 "original"} :licenses {2 "approved"}}
                                  {:actor "applicant" :type ::save-draft :items {1 "updated"} :licenses {3 "approved"}}]
                                 injections)
                 (select-keys relevant-application-keys)))))
    (testing "draft cannot be updated after submitting"
      (let [application (apply-commands application
                                        [{:actor "applicant" :type ::save-draft :items {1 "original"} :licenses {2 "original"}}
                                         {:actor "applicant" :type ::submit}]
                                        injections)]
        (is (= {:errors [{:type :invalid-state :state ::submitted}]}
               (handle-command {:type ::save-draft
                                :actor "applicant"
                                :items {1 "updated"} :licenses {2 "updated"}}
                               application
                               injections)))))
    (testing "draft can be updated after returning it to applicant"
      (is (= {:state ::returned
              :form-contents {:items {1 "updated"}
                              :licenses {3 "approved"}}
              :submitted-form-contents {:items {1 "original"}
                                        :licenses {2 "approved"}}
              :previous-submitted-form-contents nil}
             (-> (apply-commands application
                                 [{:actor "applicant" :type ::save-draft :items {1 "original"} :licenses {2 "approved"}}
                                  {:actor "applicant" :type ::submit}
                                  {:actor "assistant" :type ::return}
                                  {:actor "applicant" :type ::save-draft :items {1 "updated"} :licenses {3 "approved"}}]
                                 injections)
                 (select-keys relevant-application-keys)))))
    (testing "resubmitting remembers the previous and current application"
      (is (= {:state ::submitted
              :form-contents {:items {1 "updated"}
                              :licenses {3 "approved"}}
              :submitted-form-contents {:items {1 "updated"}
                                        :licenses {3 "approved"}}
              :previous-submitted-form-contents {:items {1 "original"}
                                                 :licenses {2 "approved"}}}
             (-> (apply-commands application
                                 [{:actor "applicant" :type ::save-draft :items {1 "original"} :licenses {2 "approved"}}
                                  {:actor "applicant" :type ::submit}
                                  {:actor "assistant" :type ::return}
                                  {:actor "applicant" :type ::save-draft :items {1 "updated"} :licenses {3 "approved"}}
                                  {:actor "applicant" :type ::submit}]
                                 injections)
                 (select-keys relevant-application-keys)))))))

(deftest test-submit-approve-or-reject
  (let [injections {:validate-form (constantly nil)}
        expected-errors [{:type :t.form.validation/required}]
        fail-injections {:validate-form (constantly expected-errors)}
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}])]
    (testing "only applicant can submit"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:actor "not-applicant" :type ::submit} application injections))))
    (testing "can only submit valid form"
      (is (= {:errors expected-errors}
             (handle-command {:actor "applicant" :type ::submit} application fail-injections))))
    (let [submitted (apply-command application {:actor "applicant" :type ::submit} injections)]
      (testing "cannot submit twice"
        (is (= {:errors [{:type :invalid-state :state ::submitted}]}
               (handle-command {:actor "applicant" :type ::submit} submitted injections))))
      (testing "submitter is member"
        (is (= [{:userid "applicant"}] (:members submitted))))
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
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}])
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
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/submitted
                                    :event/actor "applicant"}])
        injections {:valid-user? #{"deity"}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
             (handle-command {:actor "assistant" :decider "deity" :type ::request-decision}
                             application
                             {}))))
    (testing "decider must be a valid user"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "deity2"}]}
             (handle-command {:actor "assistant" :decider "deity2" :type ::request-decision}
                             application
                             injections))))
    (testing "deciding before ::request-decision should fail"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:actor "deity" :decision :approved :type ::decide}
                             application
                             injections))))
    (let [requested (apply-command application {:actor "assistant" :decider "deity" :type ::request-decision} injections)]
      (testing "request decision succesfully"
        (is (= {:decider "deity"} (select-keys requested [:decider :decision]))))
      (testing "only the requested user can decide"
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:actor "deity2" :decision :approved :type ::decide}
                               requested
                               injections))))
      (let [approved (apply-command requested {:actor "deity" :decision :approved :type ::decide} injections)]
        (testing "succesfully approved"
          (is (= {:decision :approved} (select-keys approved [:decider :decision]))))
        (testing "cannot approve twice"
          (is (= {:errors [{:type :forbidden}]}
                 (handle-command {:actor "deity" :decision :approved :type ::decide}
                                 approved
                                 injections)))))
      (let [rejected (apply-command requested {:actor "deity" :decision :rejected :type ::decide} injections)]
        (testing "successfully rejected"
          (is (= {:decision :rejected} (select-keys rejected [:decider :decision]))))
        (testing "can not reject twice"
          (is (= {:errors [{:type :forbidden}]}
                 (handle-command {:actor "deity" :decision :rejected :type ::decide}
                                 rejected
                                 injections)))))
      (testing "other decisions are not possible"
        (is (= {:errors [{:type :invalid-decision :decision :foobar}]}
               (handle-command {:actor "deity" :decision :foobar :type ::decide}
                               requested
                               injections)))))))

(deftest test-add-member
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/submitted
                                    :event/actor "applicant"}
                                   {:event/type :application.event/member-added
                                    :event/actor "applicant"
                                    :application/member {:userid "somebody"}}])
        injections {:valid-user? #{"member1" "member2" "somebody" "applicant"}}]
    (testing "add two members"
      (is (= [{:userid "applicant"} {:userid "somebody"} {:userid "member1"}]
             (:members
              (apply-commands application
                              [{:type ::add-member :actor "assistant" :member {:userid "member1"}}]
                              injections)))))
    (testing "only handler can add members"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:type ::add-member :actor "member1" :member "member1"}
                             application
                             injections)
             (handle-command {:type ::add-member :actor "applicant" :member "member1"}
                             application
                             injections))))
    (testing "only valid users can be added"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "member3"}]}
             (handle-command {:type ::add-member :actor "assistant" :member {:userid "member3"}}
                             application
                             injections))))))

(deftest test-invite-member
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}])
        injections {:valid-user? #{"somebody" "applicant"}}]
    (testing "invite two members by applicant"
      (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"} {:name "Member Applicant 2" :email "member2@applicants.com"}]
             (:invited-members
              (apply-commands application
                              [{:type ::invite-member :actor "applicant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                               {:type ::invite-member :actor "applicant" :member {:name "Member Applicant 2" :email "member2@applicants.com"}}]
                              injections)))))
    (testing "invite two members by handler"
      (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"} {:name "Member Applicant 2" :email "member2@applicants.com"}]
             (:invited-members
              (apply-commands (assoc application :state ::submitted)
                              [{:type ::invite-member :actor "assistant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                               {:type ::invite-member :actor "assistant" :member {:name "Member Applicant 2" :email "member2@applicants.com"}}]
                              injections)))))
    (testing "only applicant or handler can invite members"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:type ::invite-member :actor "member1" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                             application
                             injections))))
    (testing "applicant can't invite members to approved application"
      (is (= {:errors [{:type :invalid-state :state ::approved}]}
             (handle-command {:type ::invite-member :actor "applicant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                             (assoc application :state ::approved)
                             injections))))
    (testing "handler can invite members to approved application"
      (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"}]
             (:invited-members
              (apply-commands (assoc application :state ::approved)
                              [{:type ::invite-member :actor "assistant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}]
                              injections)))))))

(deftest test-remove-member
  (let [application {:state ::submitted
                     :members [{:userid "applicant"} {:userid "somebody"}]
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        injections {}]
    (testing "remove member by applicant"
      (is (= [{:userid "applicant"}]
             (:members
              (apply-commands application
                              [{:type ::remove-member :actor "applicant" :member {:userid "somebody"}}]
                              injections)))))
    (testing "remove member by handler"
      (is (= [{:userid "applicant"}]
             (:members
              (apply-commands application
                              [{:type ::remove-member :actor "assistant" :member {:userid "somebody"}}]
                              injections)))))
    (testing "only members can be removed"
      (is (= {:errors [{:type :user-not-member :user {:userid "notamember"}}]}
             (handle-command {:type ::remove-member :actor "assistant" :member {:userid "notamember"}}
                             application
                             injections))))))

(deftest test-uninvite-member
  (let [application {:state ::submitted
                     :invited-members [{:name "Some Body" :email "some@body.com"}]
                     :applicantuserid "applicant"
                     :workflow {:type :workflow/dynamic
                                :handlers ["assistant"]}}
        injections {}]
    (testing "uninvite member by applicant"
      (is (= []
             (:invited-members
              (apply-commands application
                              [{:type ::uninvite-member :actor "applicant" :member {:name "Some Body" :email "some@body.com"}}]
                              injections)))))
    (testing "uninvite member by handler"
      (is (= []
             (:invited-members
              (apply-commands application
                              [{:type ::uninvite-member :actor "assistant" :member {:name "Some Body" :email "some@body.com"}}]
                              injections)))))
    (testing "only invited members can be uninvited"
      (is (= {:errors [{:type :user-not-member :user {:name "Not Member" :email "not@member.com"}}]}
             (handle-command {:type ::uninvite-member :actor "assistant" :member {:name "Not Member" :email "not@member.com"}}
                             application
                             injections))))))

(deftest test-comment
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/submitted
                                    :event/actor "applicant"}])
        injections {:valid-user? #{"commenter" "commenter2" "commenter3"}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
             (handle-command {:actor "assistant" :commenters ["commenter"] :type ::request-comment}
                             application
                             {}))))
    (testing "commenters must not be empty"
      (is (= {:errors [{:type :must-not-be-empty :key :commenters}]}
             (handle-command {:actor "assistant" :commenters [] :type ::request-comment}
                             application
                             {}))))
    (testing "commenters must be a valid users"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "invaliduser"} {:type :t.form.validation/invalid-user :userid "invaliduser2"}]}
             (handle-command {:actor "assistant" :commenters ["invaliduser" "commenter" "invaliduser2"] :type ::request-comment}
                             application
                             injections))))
    (testing "commenting before ::request-comment should fail"
      (is (= {:errors [{:type :forbidden}]}
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
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:actor "commenter3" :comment "..." :type ::comment}
                               requested
                               injections))))
      (let [commented (apply-command requested {:actor "commenter" :comment "..." :type ::comment} injections)]
        (testing "succesfully commented"
          (is (= #{"commenter2"} (:commenters commented))))
        (testing "cannot comment twice"
          (is (= {:errors [{:type :forbidden}]}
                 (handle-command {:actor "commenter" :comment "..." :type ::comment}
                                 commented
                                 injections))))
        (testing "other commenter can also comment"
          (is (= #{} (:commenters (apply-command commented
                                                 {:actor "commenter2" :comment "..." :type ::comment}
                                                 injections)))))))))

(deftest test-possible-commands
  (let [draft (apply-events nil
                            [{:event/type :application.event/created
                              :event/actor "applicant"
                              :workflow/type :workflow/dynamic
                              :workflow.dynamic/handlers #{"assistant"}}])]
    (testing "draft"
      (is (= #{::submit ::invite-member}
             (possible-commands "applicant" draft)))
      (is (= #{}
             (possible-commands "assistant" draft)))
      (is (= #{}
             (possible-commands "somebody else" draft)))
      (testing "when there are invited members"
        (is (= #{::submit ::invite-member ::uninvite-member}
               (possible-commands "applicant" (assoc draft :invited-members [{:name "Some One" :email "some.one@example.org"}]))))))
    (let [submitted (apply-events draft [{:event/type :application.event/submitted
                                          :event/actor "applicant"}])]
      (testing "submitted"
        (is (= #{} ; TODO ::withdraw
               (possible-commands "applicant" submitted)))
        (is (= #{::approve ::reject ::return ::request-decision ::request-comment ::add-member ::invite-member}
               (possible-commands "assistant" submitted)))
        (is (= #{}
               (possible-commands "somebody else" submitted)))
        (testing "when there are invited members"
          (is (contains? (possible-commands "applicant" (assoc submitted :invited-members [{:name "Some One" :email "some.one@example.org"}]))
                         ::uninvite-member))
          (is (contains? (possible-commands "assistant" (assoc submitted :invited-members [{:name "Some One" :email "some.one@example.org"}]))
                         ::uninvite-member)))
        (testing "when there are added members"
          (is (contains? (possible-commands "applicant" (update submitted :members conj {:userid "someone"}))
                         ::remove-member))))
      (let [requested (apply-events submitted [{:event/type :application.event/comment-requested
                                                :event/actor "assistant"
                                                :application/commenters ["commenter"]}])]
        (testing "comment requested"
          (is (= #{}
                 (possible-commands "applicant" requested)))
          (is (= #{::approve ::reject ::return ::request-decision ::request-comment ::add-member ::invite-member}
                 (possible-commands "assistant" requested)))
          (is (= #{::comment}
                 (possible-commands "commenter" requested))))
        (let [commented (apply-events requested [{:event/type :application.event/commented
                                                  :event/actor "commenter"
                                                  :application/comment "..."}])]
          (testing "comment given"
            (is (= #{::approve ::reject ::return ::request-decision ::request-comment ::add-member ::invite-member}
                   (possible-commands "assistant" commented)))
            (is (= #{}
                   (possible-commands "commenter" commented))))))
      (let [requested (apply-events submitted [{:event/type :application.event/decision-requested
                                                :event/actor "assistant"
                                                :application/decider "decider"}])]
        (testing "decision requested"
          (is (= #{}
                 (possible-commands "applicant" requested)))
          (is (= #{::approve ::reject ::return ::request-decision ::request-comment ::add-member ::invite-member}
                 (possible-commands "assistant" requested)))
          (is (= #{::decide}
                 (possible-commands "decider" requested)))))
      (let [rejected (apply-events submitted [{:event/type :application.event/rejected
                                               :event/actor "assistant"}])]
        (testing "rejected"
          (is (= #{}
                 (possible-commands "applicant" rejected)))
          (is (= #{}
                 (possible-commands "assistant" rejected)))
          (is (= #{}
                 (possible-commands "somebody else" rejected)))))
      (let [approved (apply-events submitted [{:event/type :application.event/approved
                                               :event/actor "assistant"}])]
        (testing "approved"
          (is (= #{}
                 (possible-commands "applicant" approved)))
          (is (= #{::close ::add-member ::invite-member}
                 (possible-commands "assistant" approved)))
          (is (= #{}
                 (possible-commands "somebody else" approved))))
        (testing "closed"
          (let [closed (apply-events approved [{:event/type :application.event/closed
                                                :event/actor "assistant"}])]
            (is (= #{}
                   (possible-commands "applicant" closed)))
            (is (= #{}
                   (possible-commands "assistant" closed)))
            (is (= #{}
                   (possible-commands "somebody else" closed)))))))))
