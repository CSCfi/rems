(ns rems.workflow.dynamic
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.form-validation :as form-validation]
            [rems.permissions :as permissions]
            [rems.util :refer [getx]]
            [schema-refined.core :as r]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;;; Schemas

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

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
    ::accept-invitation
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
         :application/request-id s/Uuid
         :application/comment s/Str))
(s/defschema CommentRequestedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/comment-requested)
         :application/request-id s/Uuid
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
         :application/request-id s/Uuid
         :application/decision (s/enum :approved :rejected)
         :application/comment s/Str))
(s/defschema DecisionRequestedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/decision-requested)
         :application/request-id s/Uuid
         :application/deciders [s/Str]
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
                              :email s/Str}
         :invitation/token s/Str))
(s/defschema MemberJoinedEvent
  (assoc EventBase
         :event/type (s/eq :application.event/member-joined)
         :invitation/token s/Str))
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
   :application.event/member-joined MemberJoinedEvent
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

;;; Roles and permissions

(defmulti ^:private hide-sensitive-dynamic-event-content
  (fn [event] (:event/type event)))

(defmethod hide-sensitive-dynamic-event-content :default
  [event]
  event)

(defmethod hide-sensitive-dynamic-event-content :application.event/created
  [event]
  (dissoc event :workflow.dynamic/handlers))

(defmethod hide-sensitive-dynamic-event-content :application.event/member-invited
  [event]
  (dissoc event :invitation/token))

(defmethod hide-sensitive-dynamic-event-content :application.event/member-joined
  [event]
  (dissoc event :invitation/token))

(defn hide-sensitive-dynamic-events [events]
  (->> events
       (remove (comp #{:application.event/comment-requested
                       :application.event/commented
                       :application.event/decided
                       :application.event/decision-requested}
                     :event/type))
       (map hide-sensitive-dynamic-event-content)))

(defn see-application? [application user-id]
  (not= #{:everyone-else} (permissions/user-roles application user-id)))
(defmulti calculate-permissions
  (fn [_application event] (:event/type event)))

(defmethod calculate-permissions :default
  [application _event]
  application)

(def ^:private draft-permissions {:applicant [::save-draft
                                              ::submit
                                              ::remove-member
                                              ::invite-member
                                              ::uninvite-member]
                                  :member []
                                  :handler [:see-everything
                                            ::remove-member
                                            ::uninvite-member]
                                  :commenter [:see-everything]
                                  :past-commenter [:see-everything]
                                  :decider [:see-everything]
                                  :past-decider [:see-everything]
                                  :everyone-else [::accept-invitation]})

(def ^:private submitted-permissions {:applicant [::remove-member
                                                  ::uninvite-member]
                                      :handler [:see-everything
                                                ::add-member
                                                ::remove-member
                                                ::invite-member
                                                ::uninvite-member
                                                ::request-comment
                                                ::request-decision
                                                ::return
                                                ::approve
                                                ::reject]
                                      :commenter [:see-everything
                                                  ::comment]
                                      :decider [:see-everything
                                                ::decide]})

(def ^:private closed-permissions {:applicant []
                                   :handler [:see-everything]
                                   :commenter [:see-everything]
                                   :decider [:see-everything]
                                   :everyone-else nil})

(defmethod calculate-permissions :application.event/created
  [application event]
  (-> application
      (permissions/give-role-to-users :applicant [(:event/actor event)])
      (permissions/give-role-to-users :handler (:workflow.dynamic/handlers event))
      (permissions/set-role-permissions draft-permissions)))

(defmethod calculate-permissions :application.event/member-added
  [application event]
  (-> application
      (permissions/give-role-to-users :member [(get-in event [:application/member :userid])])))

(defmethod calculate-permissions :application.event/member-joined
  [application event]
  (-> application
      (permissions/give-role-to-users :member [(:event/actor event)])))

(defmethod calculate-permissions :application.event/member-removed
  [application event]
  (-> application
      (permissions/remove-role-from-user :member (get-in event [:application/member :userid]))))

(defmethod calculate-permissions :application.event/submitted
  [application _event]
  (-> application
      (permissions/set-role-permissions submitted-permissions)))

(defmethod calculate-permissions :application.event/returned
  [application _event]
  (-> application
      (permissions/set-role-permissions draft-permissions)))

(defmethod calculate-permissions :application.event/comment-requested
  [application event]
  (-> application
      (permissions/give-role-to-users :commenter (:application/commenters event))))

(defmethod calculate-permissions :application.event/commented
  [application event]
  (-> application
      (permissions/remove-role-from-user :commenter (:event/actor event))
      (permissions/give-role-to-users :past-commenter [(:event/actor event)]))) ; allow to still view the application

(defmethod calculate-permissions :application.event/decision-requested
  [application event]
  (-> application
      (permissions/give-role-to-users :decider (:application/deciders event))))

(defmethod calculate-permissions :application.event/decided
  [application event]
  (-> application
      (permissions/remove-role-from-user :decider (:event/actor event))
      (permissions/give-role-to-users :past-decider [(:event/actor event)]))) ; allow to still view the application

(defmethod calculate-permissions :application.event/approved
  [application _event]
  (-> application
      (permissions/set-role-permissions (update closed-permissions
                                                :handler set/union #{::add-member
                                                                     ::remove-member
                                                                     ::invite-member
                                                                     ::uninvite-member
                                                                     ::close}))))

(defmethod calculate-permissions :application.event/rejected
  [application _event]
  (-> application
      (permissions/set-role-permissions closed-permissions)))

(defmethod calculate-permissions :application.event/closed
  [application _event]
  (-> application
      (permissions/set-role-permissions closed-permissions)))

(deftest test-calculate-permissions
  ;; TODO: is this what we want? wouldn't it be useful to be able to write more than one comment?
  (testing "commenter may comment only once"
    (let [requested (reduce calculate-permissions nil [{:event/type :application.event/created
                                                        :event/actor "applicant"
                                                        :workflow.dynamic/handlers ["handler"]}
                                                       {:event/type :application.event/submitted
                                                        :event/actor "applicant"}
                                                       {:event/type :application.event/comment-requested
                                                        :event/actor "handler"
                                                        :application/commenters ["commenter1" "commenter2"]}])
          commented (reduce calculate-permissions requested [{:event/type :application.event/commented
                                                              :event/actor "commenter1"}])]
      (is (= #{:see-everything ::comment}
             (permissions/user-permissions requested "commenter1")))
      (is (= #{:see-everything}
             (permissions/user-permissions commented "commenter1")))
      (is (= #{:see-everything ::comment}
             (permissions/user-permissions commented "commenter2")))))

  (testing "decider may decide only once"
    (let [requested (reduce calculate-permissions nil [{:event/type :application.event/created
                                                        :event/actor "applicant"
                                                        :workflow.dynamic/handlers ["handler"]}
                                                       {:event/type :application.event/submitted
                                                        :event/actor "applicant"}
                                                       {:event/type :application.event/decision-requested
                                                        :event/actor "handler"
                                                        :application/deciders ["decider"]}])
          decided (reduce calculate-permissions requested [{:event/type :application.event/decided
                                                            :event/actor "decider"}])]
      (is (= #{:see-everything ::decide}
             (permissions/user-permissions requested "decider")))
      (is (= #{:see-everything}
             (permissions/user-permissions decided "decider")))))

  (testing "everyone can accept invitation"
    (let [created (reduce calculate-permissions nil [{:event/type :application.event/created
                                                      :event/actor "applicant"
                                                      :workflow.dynamic/handlers ["handler"]}])]
      (is (= #{::accept-invitation}
             (permissions/user-permissions created "joe")))))
  (testing "nobody can accept invitation for closed application"
    (let [closed (reduce calculate-permissions nil [{:event/type :application.event/created
                                                     :event/actor "applicant"
                                                     :workflow.dynamic/handlers ["handler"]}
                                                    {:event/type :application.event/closed
                                                     :event/actor "applicant"}])]
      (is (= #{}
             (permissions/user-permissions closed "joe")
             (permissions/user-permissions closed "applicant"))))))

;;; Application model

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
         :members [{:userid (:event/actor event)}]
         :form/id (:form/id event)
         :application/licenses (:application/licenses event)
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
         :deciders #{}
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
  (-> application
      (update :deciders into (:application/deciders event))
      (update ::latest-decision-request-by-user merge (zipmap (:application/deciders event)
                                                              (repeat (:application/request-id event))))))

(defmethod apply-event [:application.event/decided :workflow/dynamic]
  [application _workflow event]
  ;; we don't store the decisions in the state, they're available via
  ;; the event list
  (-> application
      (update :deciders disj (:event/actor event))
      (update ::latest-decision-request-by-user dissoc (:event/actor event))))

(defmethod apply-event [:application.event/comment-requested :workflow/dynamic]
  [application _workflow event]
  (-> application
      (update :commenters into (:application/commenters event))
      (update ::latest-comment-request-by-user merge (zipmap (:application/commenters event)
                                                             (repeat (:application/request-id event))))))

(defmethod apply-event [:application.event/commented :workflow/dynamic]
  [application _workflow event]
  ;; we don't store the comments in the state, they're available via
  ;; the event list
  (-> application
      (update :commenters disj (:event/actor event))
      (update ::latest-comment-request-by-user dissoc (:event/actor event))))

(defmethod apply-event [:application.event/member-added :workflow/dynamic]
  [application _workflow event]
  (update application :members #(vec (conj % (:application/member event)))))

(defmethod apply-event [:application.event/member-invited :workflow/dynamic]
  [application _workflow event]
  (-> application
      (update :invited-members #(vec (conj % (:application/member event))))
      (update :invitation-tokens assoc (:invitation/token event) (:application/member event))))

(defmethod apply-event [:application.event/member-joined :workflow/dynamic]
  [application _workflow event]
  (let [member-by-token ((:invitation-tokens application) (:invitation/token event))]
    (-> application
        (update :members #(vec (conj % {:userid (:event/actor event)})))
        (update :invited-members #(remove #{member-by-token} %))
        (update :invitation-tokens dissoc (:invitation/token event)))))

(defmethod apply-event [:application.event/member-removed :workflow/dynamic]
  [application _workflow event]
  (update application :members #(vec (remove #{(:application/member event)} %))))

(defmethod apply-event [:application.event/member-uninvited :workflow/dynamic]
  [application _workflow event]
  (update application :invited-members #(vec (remove #{(:application/member event)} %))))

(defn apply-events [application events]
  (reduce (fn [application event] (-> (apply-event application (:workflow application) event)
                                      (calculate-permissions event)))
          application
          events))

(defn clean-internal-state [application]
  (dissoc application ::latest-comment-request-by-user ::latest-decision-request-by-user))

;;; Command handlers

(defmulti command-handler
  "Handles a command by an event."
  (fn [cmd _application _injections] (:type cmd)))

(defn get-command-types
  "Fetch sequence of supported command names."
  []
  (keys (methods command-handler)))

(deftest test-all-command-types-handled
  (is (= CommandTypes (set (get-command-types)))))

(defn- invalid-user-error [user-id injections]
  (cond
    (not (:valid-user? injections)) {:errors [{:type :missing-injection :injection :valid-user?}]}
    (not ((:valid-user? injections) user-id)) {:errors [{:type :t.form.validation/invalid-user :userid user-id}]}))

(defn- invalid-users-errors
  "Checks the given users for validity and merges the errors"
  [user-ids injections]
  (apply merge-with into (keep #(invalid-user-error % injections) user-ids)))

(defn- validation-error [application {:keys [get-form]}]
  (let [form (get-form (:form/id application))
        _ (assert form)
        answers (:form-contents application)
        legacy-form {:items (for [field (:items form)]
                              (assoc field :value (get-in answers [:items (:id field)])))
                     :licenses (for [license (:application/licenses application)]
                                 {:id (:license/id license)
                                  :approved (= "approved"
                                               (get-in answers [:licenses (:license/id license)]))})}
        result (form-validation/validate legacy-form)]
    (when-not (= :valid result)
      {:errors result})))

(defn- valid-invitation-token? [application token]
  (contains? (:invitation-tokens application) token))

(defn- invitation-token-error [application token]
  (when-not (valid-invitation-token? application token)
    {:errors [{:type :t.actions.errors/invalid-token :token token}]}))

(defn already-member-error [application userid]
  (when (contains? (set (map :userid (:members application))) userid)
    {:errors [{:type :already-member :application-id (:id application)}]}))

(defn- ok [event]
  {:success true
   :result event})

(defmethod command-handler ::save-draft
  [cmd _application _injections]
  (ok {:event/type :application.event/draft-saved
       :application/field-values (:items cmd)
       :application/accepted-licenses (->> (:licenses cmd)
                                           (filter #(= "approved" (second %)))
                                           (map first)
                                           set)}))

(defmethod command-handler ::submit
  [cmd application injections]
  (or (validation-error application injections)
      (ok {:event/type :application.event/submitted})))

(defmethod command-handler ::approve
  [cmd _application _injections]
  (ok {:event/type :application.event/approved
       :application/comment (:comment cmd)}))

(defmethod command-handler ::reject
  [cmd _application _injections]
  (ok {:event/type :application.event/rejected
       :application/comment (:comment cmd)}))

(defmethod command-handler ::return
  [cmd _application _injections]
  (ok {:event/type :application.event/returned
       :application/comment (:comment cmd)}))

(defmethod command-handler ::close
  [cmd _application _injections]
  (ok {:event/type :application.event/closed
       :application/comment (:comment cmd)}))

(defn- must-not-be-empty [cmd key]
  (when-not (seq (get cmd key))
    {:errors [{:type :must-not-be-empty :key key}]}))

(defmethod command-handler ::request-decision
  [cmd _application injections]
  (or (must-not-be-empty cmd :deciders)
      (invalid-users-errors (:deciders cmd) injections)
      (ok {:event/type :application.event/decision-requested
           :application/request-id (java.util.UUID/randomUUID)
           :application/deciders (:deciders cmd)
           :application/comment (:comment cmd)})))

(defn- actor-is-not-decider-error [application cmd]
  (when-not (contains? (:deciders application) (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod command-handler ::decide
  [cmd application _injections]
  (or (actor-is-not-decider-error application cmd)
      (when-not (contains? #{:approved :rejected} (:decision cmd))
        {:errors [{:type :invalid-decision :decision (:decision cmd)}]})
      (let [last-request-for-actor (get-in application [::latest-decision-request-by-user (:actor cmd)])]
        (ok {:event/type :application.event/decided
             :application/request-id last-request-for-actor
             :application/decision (:decision cmd)
             :application/comment (:comment cmd)}))))

(defmethod command-handler ::request-comment
  [cmd _application injections]
  (or (must-not-be-empty cmd :commenters)
      (invalid-users-errors (:commenters cmd) injections)
      (ok {:event/type :application.event/comment-requested
           :application/request-id (java.util.UUID/randomUUID)
           :application/commenters (:commenters cmd)
           :application/comment (:comment cmd)})))

(defn- actor-is-not-commenter-error [application cmd]
  (when-not (contains? (:commenters application) (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod command-handler ::comment
  [cmd application _injections]
  (or (actor-is-not-commenter-error application cmd)
      (let [last-request-for-actor (get-in application [::latest-comment-request-by-user (:actor cmd)])]
        (ok {:event/type :application.event/commented
             ;; Currently we want to tie all comments to the latest request.
             ;; In the future this might change so that commenters can freely continue to comment
             ;; on any request they have gotten.
             :application/request-id last-request-for-actor
             :application/comment (:comment cmd)}))))

(defmethod command-handler ::add-member
  [cmd application injections]
  (or (invalid-user-error (:userid (:member cmd)) injections)
      (already-member-error application (:userid (:member cmd)))
      (ok {:event/type :application.event/member-added
           :application/member (:member cmd)})))

(defmethod command-handler ::invite-member
  [cmd _application injections]
  (ok {:event/type :application.event/member-invited
       :application/member (:member cmd)
       :invitation/token ((getx injections :secure-token))}))

(defmethod command-handler ::accept-invitation
  [cmd application injections]
  (or (invalid-user-error (:actor cmd) injections)
      (already-member-error application (:actor cmd))
      (invitation-token-error application (:token cmd))
      {:success true
       :result {:event/type :application.event/member-joined
                :event/time (:time cmd)
                :event/actor (:actor cmd)
                :application/id (:application-id cmd)
                :invitation/token (:token cmd)}}))

(defmethod command-handler ::remove-member
  [cmd application _injections]
  (or (when (= (:applicantuserid application) (:userid (:member cmd)))
        {:errors [{:type :cannot-remove-applicant}]})
      (when-not (contains? (set (map :userid (:members application)))
                           (:userid (:member cmd)))
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (ok {:event/type :application.event/member-removed
           :application/member (:member cmd)
           :application/comment (:comment cmd)})))

(defmethod command-handler ::uninvite-member
  [cmd application _injections]
  (or (when-not (contains? (set (map (juxt :name :email) (:invited-members application)))
                           [(:name (:member cmd))
                            (:email (:member cmd))])
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (ok {:event/type :application.event/member-uninvited
           :application/member (:member cmd)
           :application/comment (:comment cmd)})))

(defn- add-common-event-fields-from-command [event cmd]
  (assoc event
         :event/time (:time cmd)
         :event/actor (:actor cmd)
         :application/id (:application-id cmd)))

(defn- enrich-result [result cmd]
  (if (:success result)
    (update result :result add-common-event-fields-from-command cmd)
    result))

(defn handle-command [cmd application injections]
  (let [permissions (permissions/user-permissions application (:actor cmd))]
    (if (contains? permissions (:type cmd))
      (-> (command-handler cmd application injections)
          (enrich-result cmd))
      {:errors (or (:errors (command-handler cmd application injections)) ; prefer more specific error
                   [{:type :forbidden}])})))

(deftest test-handle-command
  (let [application (apply-events nil [{:event/type :application.event/created
                                        :event/actor "applicant"
                                        :workflow/type :workflow/dynamic
                                        :workflow.dynamic/handlers #{"assistant"}}])
        command {:type ::save-draft
                 :actor "applicant"}]
    (testing "executes command when user is authorized"
      (is (:success (handle-command command application {}))))
    (testing "fails when user is not authorized"
      ;; the permission checks should happen before executing the command handler
      ;; and only depend on the roles and permissions
      (let [application (permissions/remove-role-from-user application :applicant "applicant")
            result (handle-command command application {})]
        (is (not (:success result)))
        (is (= [{:type :forbidden}] (:errors result)))))))

(defmacro assert-ex
  "Like assert but throw the result with ex-info and not as string. "
  ([x message]
   `(when-not ~x
      (throw (ex-info (str "Assert failed: " ~message "\n" (pr-str '~x))
                      (merge ~message {:expression '~x}))))))


(defmacro try-catch-ex
  "Wraps the code in `try` and `catch` and automatically unwraps the possible exception `ex-data` into regular result."
  [& body]
  `(try
     ~@body
     (catch RuntimeException e#
       (ex-data e#))))

(defn- apply-command
  ([application cmd]
   (apply-command application cmd nil))
  ([application cmd injections]
   (let [result (handle-command cmd application injections)
         _ (assert-ex (:success result) {:cmd cmd :result result})
         event (getx result :result)]
     (-> (apply-event application (:workflow application) event)
         (calculate-permissions event)))))

(defn- apply-commands
  ([application commands]
   (apply-commands application commands nil))
  ([application commands injections]
   (reduce (fn [app cmd] (apply-command app cmd injections))
           application commands)))


;;; Possible commands

(defn possible-commands
  "Returns the commands which the user is authorized to execute."
  [actor application-state]
  (permissions/user-permissions application-state actor))

(defn assoc-possible-commands [actor application-state]
  (assoc application-state
         :possible-commands (possible-commands actor application-state)))

;;; Tests

(deftest test-save-draft
  (let [injections {:get-form {1 {}}}
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :form/id 1
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
        (is (= {:errors [{:type :forbidden}]}
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

(deftest test-submit
  (let [injections {:get-form {40 {:items [{:id 41
                                            :optional false}
                                           {:id 42
                                            :optional false}]}}}
        run-cmd (fn [events command]
                  (let [application (apply-events nil events)]
                    (handle-command command application injections)))

        created-event {:event/type :application.event/created
                       :event/time (DateTime. 1000)
                       :event/actor "applicant"
                       :application/id 1
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses [{:license/id 30}
                                              {:license/id 31}]
                       :form/id 40
                       :workflow/id 50
                       :workflow/type :workflow/dynamic
                       :workflow.dynamic/handlers #{"handler"}}
        draft-saved-event {:event/type :application.event/draft-saved
                           :event/time (DateTime. 2000)
                           :event/actor "applicant"
                           :application/id 1
                           :application/field-values {41 "foo"
                                                      42 "bar"}
                           :application/accepted-licenses #{30 31}}
        submit-command {:type ::submit
                        :time (DateTime. 3000)
                        :actor "applicant"
                        :application-id 1}]

    (testing "can submit a valid form"
      (is (= {:success true
              :result {:event/type :application.event/submitted
                       :event/time (DateTime. 3000)
                       :event/actor "applicant"
                       :application/id 1}}
             (run-cmd [created-event
                       draft-saved-event]
                      submit-command))))

    (testing "cannot submit when required fields are empty"
      (is (= {:errors [{:type :t.form.validation/required
                        :field-id 41}]}
             (run-cmd [created-event
                       (assoc-in draft-saved-event [:application/field-values 41] "")]
                      submit-command))))

    (testing "cannot submit when not all licenses are accepted"
      (is (= {:errors [{:type :t.form.validation/required
                        :license-id 31}]}
             (run-cmd [created-event
                       (update-in draft-saved-event [:application/accepted-licenses] disj 31)]
                      submit-command))))))

(deftest test-submit-approve-or-reject
  (let [injections {:get-form {1 {:items [{:id 10
                                           :optional false}]}}}
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :form/id 1
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/draft-saved
                                    :event/actor "applicant"
                                    :application/field-values {10 "foo"}
                                    :application/accepted-licenses #{}}])]
    (testing "non-applicant cannot submit"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:actor "not-applicant" :type ::submit} application injections))))
    (testing "cannot submit non-valid forms"
      (let [application (apply-events application [{:event/type :application.event/draft-saved
                                                    :event/actor "applicant"
                                                    :application/field-values {10 ""}
                                                    :application/accepted-licenses #{}}])]
        (is (= {:errors [{:type :t.form.validation/required :field-id 10}]}
               (handle-command {:actor "applicant" :type ::submit} application injections)))))
    (let [submitted (apply-command application {:actor "applicant" :type ::submit} injections)]
      (testing "cannot submit twice"
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:actor "applicant" :type ::submit} submitted injections))))
      (testing "approving"
        (is (= ::approved (:state (apply-command submitted
                                                 {:actor "assistant" :type ::approve}
                                                 injections)))))
      (testing "rejecting"
        (is (= ::rejected (:state (apply-command submitted
                                                 {:actor "assistant" :type ::reject}
                                                 injections))))))))

(deftest test-submit-return-submit-approve-close
  (let [injections {:get-form {1 {}}}
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :form/id 1
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
             (handle-command {:actor "assistant" :deciders ["deity"] :type ::request-decision}
                             application
                             {}))))
    (testing "decider must be a valid user"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "deity2"}]}
             (handle-command {:actor "assistant" :deciders ["deity2"] :type ::request-decision}
                             application
                             injections))))
    (testing "deciding before ::request-decision should fail"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:actor "deity" :decision :approved :type ::decide}
                             application
                             injections))))
    (let [requested (apply-command application {:actor "assistant" :deciders ["deity"] :type ::request-decision} injections)]
      (testing "request decision succesfully"
        (is (= #{"deity"} (:deciders requested))))
      (testing "only the requested user can decide"
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:actor "deity2" :decision :approved :type ::decide}
                               requested
                               injections))))
      (let [approved (apply-command requested {:actor "deity" :decision :approved :type ::decide} injections)]
        (testing "succesfully approved"
          (is (= #{} (:deciders approved))))
        (testing "cannot approve twice"
          (is (= {:errors [{:type :forbidden}]}
                 (handle-command {:actor "deity" :decision :approved :type ::decide}
                                 approved
                                 injections)))))
      (let [rejected (apply-command requested {:actor "deity" :decision :rejected :type ::decide} injections)]
        (testing "successfully rejected"
          (is (= #{} (:deciders rejected))))
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
             (handle-command {:type ::add-member :actor "applicant" :member {:userid "member1"}}
                             application
                             injections)
             (handle-command {:type ::add-member :actor "member1" :member {:userid "member2"}}
                             application
                             injections))))
    (testing "only valid users can be added"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "member3"}]}
             (handle-command {:type ::add-member :actor "assistant" :member {:userid "member3"}}
                             application
                             injections))))
    (testing "added members can see the application"
      (is (-> (apply-commands application
                              [{:type ::add-member :actor "assistant" :member {:userid "member1"}}]
                              injections)
              (see-application? "member1"))))))

(deftest test-invite-member
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}])
        injections {:valid-user? #{"somebody" "applicant"}
                    :secure-token (constantly "very-secure")}]
    (testing "invite two members by applicant"
      (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"} {:name "Member Applicant 2" :email "member2@applicants.com"}]
             (:invited-members
              (apply-commands application
                              [{:type ::invite-member :actor "applicant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                               {:type ::invite-member :actor "applicant" :member {:name "Member Applicant 2" :email "member2@applicants.com"}}]
                              injections)))))
    (is (= "very-secure"
           (:invitation/token
            (:result
             (handle-command {:type ::invite-member :actor "applicant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}} application injections))))
        "should generate secure token")
    (testing "invite two members by handler"
      (let [application (apply-events application [{:event/type :application.event/submitted
                                                    :event/actor "applicant"}])]
        (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"} {:name "Member Applicant 2" :email "member2@applicants.com"}]
               (:invited-members
                (apply-commands application
                                [{:type ::invite-member :actor "assistant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                                 {:type ::invite-member :actor "assistant" :member {:name "Member Applicant 2" :email "member2@applicants.com"}}]
                                injections))))))
    (testing "only applicant or handler can invite members"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:type ::invite-member :actor "member1" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                             application
                             injections))))
    (let [submitted (apply-events application
                                  [{:event/type :application.event/submitted
                                    :event/actor "applicant"}])]
      (testing "applicant can't invite members to submitted application"
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:type ::invite-member :actor "applicant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                               submitted
                               injections))))
      (testing "handler can invite members to submitted application"
        (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"}]
               (:invited-members
                (apply-commands submitted
                                [{:type ::invite-member :actor "assistant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}]
                                injections))))))))

(deftest test-accept-invitation
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/member-invited
                                    :event/:actor "applicant"
                                    :application/member {:name "Some Body" :email "somebody@applicants.com"}
                                    :invitation/token "very-secure"}])
        injections {:valid-user? #{"somebody" "somebody2" "applicant"}}]
    (testing "invitation token is available before use"
      (is (= ["very-secure"]
             (keys (:invitation-tokens application)))))

    (testing "invitation token is not available after use"
      (is (empty?
           (keys (:invitation-tokens
                  (apply-commands application
                                  [{:type ::accept-invitation :actor "somebody" :token "very-secure"}]
                                  injections))))))

    (testing "invited member can join draft"
      (is (= [{:userid "applicant"} {:userid "somebody"}]
             (:members
              (apply-commands application
                              [{:type ::accept-invitation :actor "somebody" :token "very-secure"}]
                              injections)))))

    (let [application (apply-events application
                                    [{:event/type :application.event/member-added
                                      :event/actor "applicant"
                                      :application/member {:userid "somebody"}}])]
      (testing "invited member can't join if they are already a member"
        (is (= {:errors [{:type :already-member :application-id (:id application)}]}
               (:result (try-catch-ex
                         (apply-command application
                                        {:type ::accept-invitation :actor "somebody" :token "very-secure"}
                                        injections)))))))

    (testing "invalid token can't be used to join"
      (is (= {:errors [{:type :t.actions.errors/invalid-token :token "wrong-token"}]}
             (:result
              (try-catch-ex
               (apply-commands application
                               [{:type ::accept-invitation :actor "somebody" :token "wrong-token"}]
                               injections))))))

    (testing "token can't be used twice"
      (is (= {:errors [{:type :t.actions.errors/invalid-token :token "very-secure"}]}
             (:result
              (try-catch-ex
               (apply-commands application
                               [{:type ::accept-invitation :actor "somebody" :token "very-secure"}
                                {:type ::accept-invitation :actor "somebody2" :token "very-secure"}]
                               injections))))))

    (let [submitted (apply-events application
                                  [{:event/type :application.event/submitted
                                    :event/actor "applicant"}])]
      (testing "invited member can join submitted application"
        (is (= [{:userid "applicant"} {:userid "somebody"}]
               (:members
                (apply-commands application
                                [{:type ::accept-invitation :actor "somebody" :token "very-secure"}]
                                injections)))))
      (let [closed (apply-events submitted
                                 [{:event/type :application.event/closed
                                   :event/actor "applicant"}])]
        (testing "invited member can't join a closed application"
          (is (= {:errors [{:type :forbidden}]}
                 (:result
                  (try-catch-ex
                   (apply-commands closed
                                   [{:type ::accept-invitation :actor "somebody" :token "very-secure"}]
                                   injections))))))))))

(deftest test-remove-member
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/submitted
                                    :event/actor "applicant"}
                                   {:event/type :application.event/member-added
                                    :event/actor "assistant"
                                    :application/member {:userid "somebody"}}])
        injections {:valid-user? #{"somebody" "applicant" "assistant"}}]
    (testing "remove member by applicant"
      (is (= [{:userid "applicant"}]
             (:members
              (apply-commands application
                              [{:type ::remove-member :actor "applicant" :member {:userid "somebody"}}]
                              injections)))))
    (testing "remove applicant by applicant"
      (is (= {:errors [{:type :cannot-remove-applicant}]}
             (handle-command {:type ::remove-member :actor "applicant" :member {:userid "applicant"}}
                             application
                             injections))))
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
                             injections))))
    (testing "removed members cannot see the application"
      (is (-> application
              (see-application? "somebody")))
      (is (not (-> application
                   (apply-commands [{:type ::remove-member :actor "applicant" :member {:userid "somebody"}}]
                                   injections)
                   (see-application? "somebody")))))))


(deftest test-uninvite-member
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/member-invited
                                    :event/actor "applicant"
                                    :application/member {:name "Some Body" :email "some@body.com"}}
                                   {:event/type :application.event/submitted
                                    :event/actor "applicant"}])
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
                                    [{:actor "assistant" :commenters ["commenter" "commenter2"] :type ::request-comment}
                                     ;; Make a new request that should partly override previous
                                     {:actor "assistant" :commenters ["commenter"] :type ::request-comment}]
                                    injections)]
      (testing "request comment succesfully"
        (is (= #{"commenter2" "commenter"} (:commenters requested))))
      (testing "only the requested commenter can comment"
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:actor "commenter3" :comment "..." :type ::comment}
                               requested
                               injections))))
      (testing "comments are linked to different requests"
        (is (not (= (get-in requested [::latest-comment-request-by-user "commenter"])
                    (get-in requested [::latest-comment-request-by-user "commenter2"]))))
        (is (= (get-in requested [::latest-comment-request-by-user "commenter"])
               (get-in (handle-command {:actor "commenter" :comment "..." :type ::comment}
                                       requested injections)
                       [:result :application/request-id])))
        (is (= (get-in requested [::latest-comment-request-by-user "commenter2"])
               (get-in (handle-command {:actor "commenter2" :comment "..." :type ::comment}
                                       requested injections)
                       [:result :application/request-id]))))
      (let [commented (apply-command requested {:actor "commenter" :comment "..." :type ::comment} injections)]
        (testing "succesfully commented"
          (is (= #{"commenter2"} (:commenters commented))))
        (testing "cannot comment twice"
          (is (= {:errors [{:type :forbidden}]}
                 (handle-command {:actor "commenter" :comment "..." :type ::comment}
                                 commented
                                 injections))))
        (testing "other commenter can still comment"
          (is (= #{} (:commenters (apply-command commented
                                                 {:actor "commenter2" :comment "..." :type ::comment}
                                                 injections)))))))))
