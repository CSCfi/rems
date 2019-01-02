(ns rems.api.schema
  "Shared schema definitions for the API"
  (:require [schema.core :as s])
  (:import (org.joda.time DateTime)))

(s/defschema CatalogueItem
  {:id s/Num
   :title s/Str
   :wfid s/Num
   :formid s/Num
   :resid s/Str
   :state (s/enum "enabled" "disabled")
   (s/optional-key :langcode) s/Keyword
   :localizations (s/maybe {s/Any s/Any})})

(s/defschema License
  {:id s/Num
   :licensetype (s/enum "text" "link" "attachment")
   :start DateTime
   :end (s/maybe DateTime)
   :title s/Str
   :textcontent s/Str
   :localizations {s/Keyword {:title s/Str :textcontent s/Str}}})

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
   :value (s/maybe s/Str)})

(s/defschema Event
  {:userid (s/maybe s/Str)
   :round s/Num
   :event s/Str
   :comment (s/maybe s/Str)
   :time DateTime
   :eventdata s/Any})

(s/defschema DynamicEvent
  {:actor s/Str
   :time (s/maybe DateTime) ; TODO should always have time
   (s/optional-key :comment) (s/maybe s/Str)
   s/Any s/Any})

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
   (s/optional-key :members) [s/Str]
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :workflow) s/Any
   (s/optional-key :possible-commands) #{s/Keyword}
   (s/optional-key :decider) s/Str
   (s/optional-key :decision) s/Keyword
   (s/optional-key :commenters) #{s/Str}})

(s/defschema Entitlement
  {:resource s/Str
   :application-id s/Num
   :start s/Str
   :mail s/Str})

(s/defschema SuccessResponse
  {:success s/Bool
   (s/optional-key :errors) s/Any})
