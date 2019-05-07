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
   :start s/Str
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

(s/defschema Field
  {:field/id s/Int
   :field/value s/Str
   (s/optional-key :field/previous-value) s/Str
   :field/type (s/enum :attachment :date :description :label :multiselect :option :text :texta)
   :field/title LocalizedString
   :field/placeholder LocalizedString
   :field/optional s/Bool
   :field/options [{:key s/Str
                    :label LocalizedString}]
   :field/max-length (s/maybe s/Int)})

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
