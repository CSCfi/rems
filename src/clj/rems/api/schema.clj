(ns rems.api.schema
  "Shared schema definitions for the API"
  (:require
   [schema.core :as s])
  (:import
   [org.joda.time DateTime]))

(def CatalogueItem
  {:id s/Num
   :title s/Str
   :wfid s/Num
   :formid s/Num
   :resid s/Str
   :state (s/enum "enabled" "disabled")
   (s/optional-key :langcode) s/Keyword
   :localizations (s/maybe {s/Any s/Any})})

(def License
  {:id s/Num
   :licensetype (s/enum "text" "link" "attachment")
   :start DateTime
   :end (s/maybe DateTime)
   :title s/Str
   :textcontent s/Str
   :localizations {s/Keyword {:title s/Str :textcontent s/Str}}})

(def ResourceLicense License)

(def ApplicationLicense
  (merge License
         {:type (s/eq "license") ;; TODO this is pretty redundant
          :approved s/Bool}))

(def Item
  {:id s/Num
   :localizations {s/Keyword {:title s/Str :inputprompt (s/maybe s/Str)}}
   :optional s/Bool
   :type s/Str
   :value (s/maybe s/Str)})

(def Event
  {:userid (s/maybe s/Str)
   :round s/Num
   :event s/Str
   :comment (s/maybe s/Str)
   :time DateTime})

(def Application
  {:id (s/maybe s/Num) ;; does not exist for unsaved draft
   :formid s/Num
   :state s/Str
   :applicantuserid s/Str
   (s/optional-key :start) DateTime ;; does not exist for draft
   :wfid s/Num
   (s/optional-key :curround) s/Num ;; does not exist for draft
   (s/optional-key :fnlround) s/Num ;; does not exist for draft
   :events [Event]
   (s/optional-key :can-approve?) s/Bool
   (s/optional-key :can-close?) s/Bool
   (s/optional-key :can-withdraw?) s/Bool
   (s/optional-key :can-third-party-review?) s/Bool
   (s/optional-key :is-applicant?) s/Bool
   (s/optional-key :review) (s/enum :third-party)
   :catalogue-items [CatalogueItem]
   (s/optional-key :review-type) (s/maybe (s/enum :normal :third-party))
   (s/optional-key :last-modified) DateTime})

(def Entitlement
  {:resource s/Str
   :application-id s/Num
   :start s/Str
   :mail s/Str})

(def SuccessResponse
  {:success s/Bool})
