(ns rems.api.schema
  "Shared schema definitions for the API"
  (:require [rems.workflow.dynamic :as dynamic]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(s/defschema CatalogueItem
  {:id s/Num
   :title s/Str
   :wfid s/Num
   (s/optional-key :workflow-name) s/Str
   :formid s/Num
   (s/optional-key :form-name) s/Str
   :resid s/Str
   :resource-id s/Num
   (s/optional-key :resource-name) s/Str
   :state (s/enum "enabled" "disabled")
   (s/optional-key :langcode) s/Keyword
   :start DateTime
   :localizations (s/maybe {s/Any s/Any})})

(s/defschema License
  {:id s/Num
   :licensetype (s/enum "text" "link" "attachment")
   :start DateTime
   :end (s/maybe DateTime)
   :title s/Str
   :textcontent s/Str
   (s/optional-key :attachment-id) (s/maybe s/Num)
   :localizations {s/Keyword {:title s/Str
                              :textcontent s/Str
                              (s/optional-key :attachment-id) (s/maybe s/Num)}}})

(s/defschema Licenses
  [License])

(s/defschema ResourceLicense License)

(s/defschema ApplicationLicense
  (merge License
         {:type (s/eq "license") ;; TODO this is pretty redundant
          :approved s/Bool}))

(s/defschema Item
  {:id s/Num
   :localizations {s/Keyword {:title s/Str :inputprompt (s/maybe s/Str)}}
   :optional s/Bool
   :options [{:key s/Str :label {s/Keyword s/Str}}]
   :maxlength (s/maybe s/Int)
   :type s/Str
   :value (s/maybe s/Str)
   :previous-value (s/maybe s/Str)})

(s/defschema Event
  {:userid (s/maybe s/Str)
   :round s/Num
   :event s/Str
   :comment (s/maybe s/Str)
   :time DateTime
   :eventdata s/Any})

(s/defschema InvitedMember
  {:name s/Str
   :email s/Str})

(s/defschema AddedMember
  {:userid s/Str})

(s/defschema DynamicEvent
  (assoc dynamic/EventBase
         s/Keyword s/Any))

(s/defschema Application
  {:id (s/maybe s/Num) ;; does not exist for unsaved draft
   :formid s/Num
   :state (s/cond-pre s/Str s/Keyword) ;; HACK for dynamic applications
   :applicantuserid s/Str
   (s/optional-key :start) DateTime ;; does not exist for draft
   :wfid s/Num
   (s/optional-key :curround) s/Num ;; does not exist for draft
   (s/optional-key :fnlround) s/Num ;; does not exist for draft
   (s/optional-key :events) [Event]
   (s/optional-key :dynamic-events) [DynamicEvent]
   (s/optional-key :can-approve?) s/Bool
   (s/optional-key :can-close?) s/Bool
   (s/optional-key :can-withdraw?) s/Bool
   (s/optional-key :can-third-party-review?) s/Bool
   (s/optional-key :is-applicant?) s/Bool
   (s/optional-key :review) (s/enum :third-party)
   :catalogue-items [CatalogueItem]
   (s/optional-key :review-type) (s/maybe (s/enum :normal :third-party))
   (s/optional-key :last-modified) DateTime
   (s/optional-key :invited-members) [InvitedMember]
   (s/optional-key :members) [AddedMember]
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :workflow) s/Any
   (s/optional-key :possible-commands) #{s/Keyword}
   (s/optional-key :decider) s/Str
   (s/optional-key :decision) s/Keyword
   (s/optional-key :commenters) #{s/Str}
   (s/optional-key :latest-comment-requests) {s/Str s/Uuid}
   (s/optional-key :form-contents) s/Any
   (s/optional-key :submitted-form-contents) s/Any
   (s/optional-key :previous-submitted-form-contents) s/Any})

(s/defschema Entitlement
  {:resource s/Str
   :application-id s/Num
   :start s/Str
   :mail s/Str})

(s/defschema SuccessResponse
  {:success s/Bool
   (s/optional-key :errors) [s/Any]})
