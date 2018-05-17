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
   :state s/Str
   (s/optional-key :langcode) s/Keyword
   :localizations (s/maybe {s/Any s/Any})})

(def License
  ;; NB! the license table also has a start and an end but they're
  ;; not exposed/used at the moment
  {:id s/Num
   :licensetype (s/enum "text" "link" "attachment")
   :title s/Str
   :textcontent s/Str
   :localizations {s/Keyword {:title s/Str :textcontent s/Str}}})

(def ResourceLicense
  (merge License
         {:start DateTime
          :end (s/maybe DateTime)}))

(def Resource
  {:id s/Num
   :modifieruserid s/Str
   :prefix s/Str
   :resid s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :licenses [ResourceLicense]})

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
  {:userid s/Str
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
   :catalogue-items [CatalogueItem]
   (s/optional-key :review-type) (s/enum :normal :third-party nil)
   (s/optional-key :handled) DateTime})

(def Entitlement
  {:resource s/Str
   :application-id s/Num
   :start s/Str
   :mail s/Str})
