(ns rems.application.events
  (:require [clojure.test :refer :all]
            [schema-refined.core :as r]
            [schema.core :as s]
            [rems.schema-base :as schema-base]
            [rems.util :refer [assert-ex try-catch-ex]])
  (:import (org.joda.time DateTime)))

(s/defschema EventAttachment
  {:attachment/id s/Int})

(s/defschema EventWithComment
  (assoc schema-base/EventBase
         (s/optional-key :application/comment) s/Str
         (s/optional-key :event/attachments) [EventAttachment]))

(s/defschema ApprovedEvent
  (assoc EventWithComment
         ;; single-value enums are supported by swagger, unlike s/eq.
         ;; we don't yet generate swagger for events but we might in
         ;; the future
         :event/type (s/enum :application.event/approved)
         (s/optional-key :entitlement/end) DateTime))
(s/defschema ClosedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/closed)))
(s/defschema ReviewedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/reviewed)
         :application/request-id s/Uuid))
(s/defschema ReviewRequestedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/review-requested)
         :application/request-id s/Uuid
         :application/reviewers [schema-base/UserId]))
(s/defschema CopiedFromEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/copied-from)
         :application/copied-from {:application/id s/Int
                                   :application/external-id s/Str}))
(s/defschema CopiedToEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/copied-to)
         :application/copied-to {:application/id s/Int
                                 :application/external-id s/Str}))
(def workflow-types
  #{:workflow/decider
    :workflow/default
    :workflow/master})
(s/defschema CreatedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/created)
         :application/external-id s/Str
         :application/resources [{:catalogue-item/id s/Int
                                  :resource/ext-id s/Str}]
         :application/licenses [{:license/id s/Int}]
         :application/forms [{:form/id schema-base/FormId}]
         :workflow/id s/Int
         :workflow/type (apply s/enum workflow-types)))
(s/defschema DecidedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/decided)
         :application/request-id s/Uuid
         :application/decision (s/enum :approved :rejected)))
(s/defschema DecisionRequestedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/decision-requested)
         :application/request-id s/Uuid
         :application/deciders [schema-base/UserId]))
(s/defschema DeletedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/deleted)))
(s/defschema DraftSavedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/draft-saved)
         :application/field-values [{:form schema-base/FormId
                                     :field schema-base/FieldId
                                     :value schema-base/FieldValue}]
         (s/optional-key :application/duo-codes) [schema-base/DuoCode]))
(s/defschema ExternalIdAssignedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/external-id-assigned)
         :application/external-id s/Str))
(s/defschema ExpirationNotificationsSentEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/expiration-notifications-sent)
         :last-activity DateTime
         :expires-on DateTime))
(s/defschema LicensesAcceptedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/licenses-accepted)
         :application/accepted-licenses #{s/Int}))
(s/defschema LicensesAddedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/licenses-added)
         :application/licenses [{:license/id s/Int}]))
(s/defschema MemberAddedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/member-added)
         :application/member schema-base/User))
(s/defschema MemberInvitedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/member-invited)
         :application/member {:name s/Str
                              :email s/Str}
         :invitation/token s/Str))
(s/defschema MemberJoinedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/member-joined)
         :invitation/token s/Str))
(s/defschema MemberRemovedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/member-removed)
         :application/member schema-base/User))
(s/defschema MemberUninvitedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/member-uninvited)
         :application/member {:name s/Str
                              :email s/Str}))
(s/defschema AttachmentsRedactedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/attachments-redacted)
         :event/redacted-attachments [EventAttachment]
         :application/public s/Bool))
(s/defschema ApplicantChangedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/applicant-changed)
         :application/applicant schema-base/User))
(s/defschema ReviewerInvitedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/reviewer-invited)
         :application/reviewer {:name s/Str
                                :email s/Str}
         ;; TODO allocate request-id already here?
         :invitation/token s/Str))
(s/defschema ReviewerJoinedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/reviewer-joined)
         :application/request-id s/Uuid
         :invitation/token s/Str))
(s/defschema DeciderInvitedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/decider-invited)
         :application/decider {:name s/Str
                               :email s/Str}
         ;; TODO allocate request-id already here?
         :invitation/token s/Str))
(s/defschema DeciderJoinedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/decider-joined)
         :application/request-id s/Uuid
         :invitation/token s/Str))
(s/defschema RejectedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/rejected)))
(s/defschema RemarkedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/remarked)
         :application/public s/Bool))
(s/defschema ResourcesChangedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/resources-changed)
         :application/forms [{:form/id schema-base/FormId}]
         :application/resources [{:catalogue-item/id s/Int
                                  :resource/ext-id s/Str}]
         :application/licenses [{:license/id s/Int}]))
(s/defschema ReturnedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/returned)))
(s/defschema RevokedEvent
  (assoc EventWithComment
         :event/type (s/enum :application.event/revoked)))
(s/defschema SubmittedEvent
  (assoc schema-base/EventBase
         :event/type (s/enum :application.event/submitted)))

(def event-schemas
  {:application.event/attachments-redacted AttachmentsRedactedEvent
   :application.event/applicant-changed ApplicantChangedEvent
   :application.event/approved ApprovedEvent
   :application.event/closed ClosedEvent
   :application.event/review-requested ReviewRequestedEvent
   :application.event/reviewed ReviewedEvent
   :application.event/copied-from CopiedFromEvent
   :application.event/copied-to CopiedToEvent
   :application.event/created CreatedEvent
   :application.event/decided DecidedEvent
   :application.event/decider-invited DeciderInvitedEvent
   :application.event/decider-joined DeciderJoinedEvent
   :application.event/decision-requested DecisionRequestedEvent
   :application.event/deleted DeletedEvent
   :application.event/draft-saved DraftSavedEvent
   :application.event/external-id-assigned ExternalIdAssignedEvent
   :application.event/expiration-notifications-sent ExpirationNotificationsSentEvent
   :application.event/licenses-accepted LicensesAcceptedEvent
   :application.event/licenses-added LicensesAddedEvent
   :application.event/member-added MemberAddedEvent
   :application.event/member-invited MemberInvitedEvent
   :application.event/member-joined MemberJoinedEvent
   :application.event/member-removed MemberRemovedEvent
   :application.event/member-uninvited MemberUninvitedEvent
   :application.event/rejected RejectedEvent
   :application.event/remarked RemarkedEvent
   :application.event/resources-changed ResourcesChangedEvent
   :application.event/returned ReturnedEvent
   :application.event/reviewer-invited ReviewerInvitedEvent
   :application.event/reviewer-joined ReviewerJoinedEvent
   :application.event/revoked RevokedEvent
   :application.event/submitted SubmittedEvent})

(def event-types
  (keys event-schemas))

(s/defschema Event
  (apply r/dispatch-on :event/type (flatten (seq event-schemas))))

(def ^:private validate-event-schema
  (s/validator Event))

(defn validate-event [event]
  (assert-ex (contains? event-schemas (:event/type event))
             {:error {:event/type ::unknown-type}
              :value event})
  (validate-event-schema event))

(defn validate-events [events]
  (doseq [event events]
    (validate-event event))
  events)

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
    (is (= {:application/member 'missing-required-key}
           (s/check Event
                    {:event/type :application.event/member-added
                     :event/time (DateTime.)
                     :event/actor "foo"
                     :application/id 123}))))
  (testing "unknown event type"
    (is (= "(not (some-matching-condition? a-clojure.lang.PersistentArrayMap))"
           (pr-str (s/check Event
                            {:event/type :foo
                             :event/time (DateTime.)
                             :event/actor "foo"
                             :application/id 123}))))))

(deftest test-validate-event
  (testing "check specific event schema"
    (is (validate-event {:event/type :application.event/submitted
                         :event/time (DateTime.)
                         :event/actor "foo"
                         :application/id 123})))
  (testing "missing event specific key"
    (is (= {:application/member 'missing-required-key}
           (:error
            (try-catch-ex
             (validate-event {:event/type :application.event/member-added
                              :event/time (DateTime.)
                              :event/actor "foo"
                              :application/id 123}))))))
  (testing "unknown event type"
    (is (= {:event/type ::unknown-type}
           (:error
            (try-catch-ex
             (validate-event
              {:event/type :does-not-exist
               :event/time (DateTime.)
               :event/actor "foo"
               :application/id 123})))))))
