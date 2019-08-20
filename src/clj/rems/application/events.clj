(ns rems.application.events
  (:require [clojure.test :refer :all]
            [schema-refined.core :as r]
            [schema.core :as s]
            [rems.util :refer [assert-ex try-catch-ex]])
  (:import (org.joda.time DateTime)))

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema EventBase
  {(s/optional-key :event/id) s/Int
   :event/type s/Keyword
   :event/time DateTime
   :event/actor UserId
   :application/id s/Int})

(s/defschema ApprovedEvent
  (assoc EventBase
         ;; single-value enums are supported by swagger, unlike s/eq.
         ;; we don't yet generate swagger for events but we might in
         ;; the future
         :event/type (s/enum :application.event/approved)
         :application/comment s/Str))
(s/defschema ClosedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/closed)
         :application/comment s/Str))
;; TODO Commented/CommentRequested could be renamed to Reviewed/ReviewRequested to be in line with the UI
(s/defschema CommentedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/commented)
         :application/request-id s/Uuid
         :application/comment s/Str))
(s/defschema CommentRequestedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/comment-requested)
         :application/request-id s/Uuid
         :application/commenters [s/Str]
         :application/comment s/Str))
(s/defschema CreatedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/created)
         :application/external-id (s/maybe s/Str)
         :application/resources [{:catalogue-item/id s/Int
                                  :resource/ext-id s/Str}]
         :application/licenses [{:license/id s/Int}]
         :form/id s/Int
         :workflow/id s/Int
         :workflow/type s/Keyword))
(s/defschema DecidedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/decided)
         :application/request-id s/Uuid
         :application/decision (s/enum :approved :rejected)
         :application/comment s/Str))
(s/defschema DecisionRequestedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/decision-requested)
         :application/request-id s/Uuid
         :application/deciders [s/Str]
         :application/comment s/Str))
(s/defschema DraftSavedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/draft-saved)
         :application/field-values {s/Int s/Str}))
(s/defschema LicensesAcceptedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/licenses-accepted)
         :application/accepted-licenses #{s/Int}))
(s/defschema LicensesAddedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/licenses-added)
         :application/comment s/Str
         :application/licenses [{:license/id s/Int}]))
(s/defschema MemberAddedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-added)
         :application/member {:userid UserId}))
(s/defschema MemberInvitedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-invited)
         :application/member {:name s/Str
                              :email s/Str}
         :invitation/token s/Str))
(s/defschema MemberJoinedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-joined)
         :invitation/token s/Str))
(s/defschema MemberRemovedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-removed)
         :application/member {:userid UserId}
         :application/comment s/Str))
(s/defschema MemberUninvitedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-uninvited)
         :application/member {:name s/Str
                              :email s/Str}
         :application/comment s/Str))
(s/defschema RejectedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/rejected)
         :application/comment s/Str))
(s/defschema RemarkedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/remarked)
         :application/comment s/Str
         :application/public s/Bool))
(s/defschema ResourcesChangedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/resources-changed)
         (s/optional-key :application/comment) s/Str
         :application/resources [{:catalogue-item/id s/Int
                                  :resource/ext-id s/Str}]
         :application/licenses [{:license/id s/Int}]))
(s/defschema ReturnedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/returned)
         :application/comment s/Str))
(s/defschema SubmittedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/submitted)))
(s/defschema CopiedFromEvent
  (assoc EventBase
         :event/type (s/enum :application.event/copied-from)
         :application/copied-from {:application/id s/Int
                                   :application/external-id (s/maybe s/Str)}))
(s/defschema CopiedToEvent
  (assoc EventBase
         :event/type (s/enum :application.event/copied-to)
         :application/copied-to {:application/id s/Int
                                 :application/external-id (s/maybe s/Str)}))

(def event-schemas
  {:application.event/approved ApprovedEvent
   :application.event/closed ClosedEvent
   :application.event/commented CommentedEvent
   :application.event/comment-requested CommentRequestedEvent
   :application.event/created CreatedEvent
   :application.event/decided DecidedEvent
   :application.event/decision-requested DecisionRequestedEvent
   :application.event/draft-saved DraftSavedEvent
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
   :application.event/submitted SubmittedEvent
   :application.event/copied-from CopiedFromEvent
   :application.event/copied-to CopiedToEvent})

(s/defschema Event
  (apply r/dispatch-on :event/type (flatten (seq event-schemas))))

(defn validate-event [event]
  (assert-ex (contains? event-schemas (:event/type event)) {:error {:event/type ::unknown-type}
                                                            :value event})
  (s/validate Event event))

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
    (is (= {:application/comment 'missing-required-key}
           (s/check Event
                    {:event/type :application.event/approved
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
    (is (= {:application/comment 'missing-required-key}
           (:error
            (try-catch-ex
             (validate-event {:event/type :application.event/approved
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
