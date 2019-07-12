(ns rems.api.schema
  "Shared schema definitions for the API"
  (:require [rems.application.events :as events]
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
   (s/optional-key :langcode) s/Keyword
   :start DateTime
   :end (s/maybe DateTime)
   :enabled s/Bool
   :archived s/Bool
   :expired s/Bool
   :localizations (s/maybe {s/Any s/Any})})

(s/defschema License
  {:id s/Num
   :licensetype (s/enum "text" "link" "attachment")
   :start DateTime
   :end (s/maybe DateTime)
   :enabled s/Bool
   :archived s/Bool
   :expired s/Bool
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
  (assoc events/EventBase
         s/Keyword s/Any))

(s/defschema Entitlement
  {:resource s/Str
   :application-id s/Num
   :start DateTime
   :end (s/maybe DateTime)
   :mail s/Str})

(s/defschema UpdateStateCommand
  {:id s/Num
   :enabled s/Bool
   :archived s/Bool})

(s/defschema SuccessResponse
  {:success s/Bool
   (s/optional-key :errors) [s/Any]})

(s/defschema LocalizedString
  {s/Keyword s/Str})

(s/defschema LocalizedInt
  {s/Keyword s/Int})

(s/defschema V2Resource
  {:resource/id s/Int
   :resource/ext-id s/Str
   :catalogue-item/id s/Int
   :catalogue-item/title LocalizedString
   :catalogue-item/start DateTime
   :catalogue-item/end (s/maybe DateTime)
   :catalogue-item/enabled s/Bool
   :catalogue-item/expired s/Bool
   :catalogue-item/archived s/Bool})

(s/defschema V2License
  {:license/id s/Int
   :license/type (s/enum :text :link :attachment)
   :license/title LocalizedString
   (s/optional-key :license/link) LocalizedString
   (s/optional-key :license/text) LocalizedString
   (s/optional-key :license/attachment-id) LocalizedInt
   (s/optional-key :license/attachment-filename) LocalizedString
   :license/start DateTime
   :license/end (s/maybe DateTime)
   :license/enabled s/Bool
   :license/expired s/Bool
   :license/archived s/Bool})

(def UserId s/Str)

(s/defschema Actor
  {:actoruserid UserId
   :round s/Num
   :role (s/enum "approver" "reviewer")})

(s/defschema WorkflowLicense
  {:type s/Str
   :start DateTime
   :textcontent s/Str
   :localizations [s/Any]
   :end (s/maybe DateTime)})

(s/defschema WorkflowDB ; TODO: unify workflow schemas
  {:id s/Num
   :organization s/Str
   :owneruserid UserId
   :modifieruserid UserId
   :title s/Str
   :fnlround s/Num
   :workflow s/Any
   :licenses s/Any
   :visibility s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :expired s/Bool
   :enabled s/Bool
   :archived s/Bool})

(s/defschema Workflow
  (-> WorkflowDB
      (dissoc :fnlround
              :licenses
              :visibility)
      (assoc :final-round s/Num
             :actors [Actor]
             :licenses [WorkflowLicense])))

(def not-neg? (partial <= 0))

(s/defschema FieldTemplate
  {:field/id s/Int
   :field/type (s/enum :attachment :date :description :label :multiselect :option :text :texta)
   :field/title LocalizedString
   (s/optional-key :field/placeholder) LocalizedString
   :field/optional s/Bool
   (s/optional-key :field/options) [{:key s/Str
                                     :label LocalizedString}]
   (s/optional-key :field/max-length) (s/maybe (s/constrained s/Int not-neg?))})

(s/defschema NewFieldTemplate
  (dissoc FieldTemplate :field/id))

(s/defschema Field
  (assoc FieldTemplate
         :field/value s/Str
         (s/optional-key :field/previous-value) s/Str))

(s/defschema FormTemplate
  {:form/id s/Int
   :form/organization s/Str
   :form/title s/Str
   :form/fields [FieldTemplate]
   ;; TODO: rename the following to use :status/ namespace (also in all other entities)
   :start DateTime
   :end (s/maybe DateTime)
   :expired s/Bool
   :enabled s/Bool
   :archived s/Bool})

(s/defschema FormTemplateOverview
  (dissoc FormTemplate :form/fields))

(s/defschema Form
  {:form/id s/Int
   :form/title s/Str
   :form/fields [Field]})

(s/defschema ApplicationAttachment
  {:attachment/id s/Num
   :attachment/filename s/Str
   :attachment/type s/Str})

(s/defschema Application
  {:application/id s/Int
   :application/external-id (s/maybe s/Str)
   :application/state s/Keyword
   :application/created DateTime
   :application/modified DateTime
   (s/optional-key :application/first-submitted) DateTime
   :application/last-activity DateTime
   :application/applicant s/Str
   :application/applicant-attributes {s/Keyword s/Str}
   :application/members #{{:userid s/Str
                           s/Keyword s/Str}}
   :application/invited-members #{{:name s/Str
                                   :email s/Str}}
   :application/resources [V2Resource]
   :application/licenses [V2License]
   :application/accepted-licenses (s/maybe {s/Str #{s/Num}})
   :application/events [Event]
   :application/description s/Str
   :application/form Form
   :application/workflow {:workflow/id s/Int
                          :workflow/type s/Keyword
                          (s/optional-key :workflow.dynamic/handlers) #{s/Str}}
   :application/roles #{s/Keyword}
   :application/permissions #{s/Keyword}
   :application/attachments [ApplicationAttachment]})

(s/defschema ApplicationOverview
  (dissoc Application
          :application/form
          :application/events
          :application/licenses))
