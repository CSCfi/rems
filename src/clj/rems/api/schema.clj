(ns rems.api.schema
  "Shared schema definitions for the API"
  (:require [rems.application.events :as events]
            [rems.application.commands :as commands]
            [ring.swagger.json-schema :as rjs]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(s/defschema Language
  (rjs/field s/Keyword
             {:description "A language code"
              :example "en"}))

(s/defschema LocalizedString
  (rjs/field {Language s/Str}
             {:example {:fi "text in Finnish"
                        :en "text in English"}
              :description "Text values keyed by languages"}))

(s/defschema LocalizedInt
  (rjs/field {Language s/Int}
             {:example {:fi 1
                        :en 2}
              :description "Integers keyed by languages"}))

(def UserId s/Str)
(s/defschema User {:userid UserId})
(s/defschema OrganizationId {:organization/id s/Str})

(s/defschema UserWithAttributes
  {:userid UserId
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)
   (s/optional-key :organizations) [OrganizationId]
   (s/optional-key :notification-email) (s/maybe s/Str)
   (s/optional-key :researcher-status-by) s/Str
   s/Keyword s/Any})

(s/defschema OrganizationOverview
  (merge OrganizationId
         {:organization/short-name LocalizedString
          :organization/name LocalizedString}))

(s/defschema OrganizationFull
  (merge OrganizationOverview
         {(s/optional-key :organization/modifier) UserWithAttributes
          (s/optional-key :organization/last-modified) DateTime
          (s/optional-key :organization/owners) [UserWithAttributes]
          (s/optional-key :organization/review-emails) [{:name LocalizedString
                                                         :email s/Str}]
          (s/optional-key :enabled) s/Bool
          (s/optional-key :archived) s/Bool}))

(s/defschema CatalogueItemLocalizations
  {s/Keyword {;; TODO :id (it's the catalogue item id) and :langcode
              ;; fields are redundant. If we remove them we can reuse
              ;; this schema as WriteCatalogueItemLocalizations in
              ;; rems.administration.catalogue-item
              :id s/Int
              :langcode s/Keyword
              :title s/Str
              :infourl (s/maybe s/Str)}})

(s/defschema CatalogueItem
  {:id s/Int
   :wfid s/Int
   (s/optional-key :workflow-name) s/Str
   :formid s/Int
   (s/optional-key :form-name) s/Str
   :resid s/Str
   :resource-id s/Int
   :organization OrganizationOverview
   (s/optional-key :resource-name) s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :enabled s/Bool
   :archived s/Bool
   :expired s/Bool
   :localizations CatalogueItemLocalizations})

(s/defschema License
  {:id s/Int
   :licensetype (s/enum "text" "link" "attachment")
   :organization OrganizationOverview
   :enabled s/Bool
   :archived s/Bool
   :localizations {s/Keyword {:title s/Str
                              :textcontent s/Str
                              (s/optional-key :attachment-id) (s/maybe s/Int)}}})

(s/defschema Licenses
  [License])

(s/defschema ResourceLicense License)

(s/defschema Event
  (assoc events/EventBase
         :event/actor-attributes UserWithAttributes
         s/Keyword s/Any))

(s/defschema Entitlement
  {:resource s/Str
   :user UserWithAttributes
   :application-id s/Int
   :start DateTime
   :end (s/maybe DateTime)
   :mail (rjs/field (s/maybe s/Str)
                    {:deprecate true
                     :description "DEPRECATED, will disappear"})}) ;; TODO

(s/defschema Permission
  {:type s/Str
   :value s/Str
   :source s/Str
   :by s/Str
   :asserted s/Int})

(s/defschema EnabledCommand
  {:id s/Int
   :enabled s/Bool})

(s/defschema ArchivedCommand
  {:id s/Int
   :archived s/Bool})

(s/defschema OrganizationEnabledCommand
  (merge OrganizationId
         {:enabled s/Bool}))

(s/defschema OrganizationArchivedCommand
  (merge OrganizationId
         {:archived s/Bool}))

(s/defschema SuccessResponse
  {:success s/Bool
   (s/optional-key :errors) [s/Any]})

(s/defschema V2Resource
  {:resource/id s/Int
   :resource/ext-id s/Str
   :catalogue-item/id s/Int
   :catalogue-item/title LocalizedString
   :catalogue-item/infourl LocalizedString
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
   :license/enabled s/Bool
   :license/archived s/Bool})

(s/defschema Workflow
  {:id s/Int
   :organization OrganizationOverview
   :owneruserid UserId
   :modifieruserid UserId
   :title s/Str
   :workflow s/Any
   :licenses [License]
   :enabled s/Bool
   :archived s/Bool})

(def not-neg? (partial <= 0))

(def FieldId s/Str)

;;; template for a form field, before answering
(s/defschema FieldTemplate
  {:field/id FieldId
   :field/type (s/enum :attachment :date :description :email :header :label :multiselect :option :text :texta :table)
   :field/title LocalizedString
   (s/optional-key :field/placeholder) LocalizedString
   :field/optional s/Bool
   (s/optional-key :field/options) [{:key s/Str
                                     :label LocalizedString}]
   (s/optional-key :field/columns) [{:key s/Str
                                     :label LocalizedString}]
   (s/optional-key :field/max-length) (s/maybe (s/constrained s/Int not-neg?))
   (s/optional-key :field/privacy) (rjs/field
                                    (s/enum :public :private)
                                    {:description "Public by default"})
   (s/optional-key :field/visibility) (rjs/field
                                       {:visibility/type (s/enum :always :only-if)
                                        (s/optional-key :visibility/field) {:field/id FieldId}
                                        (s/optional-key :visibility/values) [s/Str]}
                                       {:description "Always visible by default"})
   (s/optional-key :field/info-text) LocalizedString})

(s/defschema NewFieldTemplate
  (-> FieldTemplate
      (dissoc :field/id)
      (assoc (s/optional-key :field/id) FieldId)))

(s/defschema Field
  (assoc FieldTemplate
         :field/value s/Str
         :field/visible s/Bool
         :field/private s/Bool
         (s/optional-key :field/previous-value) s/Str))

(s/defschema FormTemplate
  {:form/id s/Int
   :organization OrganizationOverview
   :form/title s/Str
   :form/fields [FieldTemplate]
   (s/optional-key :form/errors) (s/maybe {(s/optional-key :organization) s/Any
                                           (s/optional-key :form/title) s/Any
                                           (s/optional-key :form/fields) {s/Num s/Any}})
   ;; TODO: rename the following to use :status/ namespace (also in all other entities)
   :enabled s/Bool
   :archived s/Bool})

(s/defschema FormTemplateOverview
  (dissoc FormTemplate :form/fields))

;;; instance for form template once filled in by user
(s/defschema Form
  {:form/id s/Int
   :form/title s/Str
   :form/fields [Field]})

(s/defschema ApplicationAttachment
  {:attachment/id s/Int
   :attachment/filename s/Str
   :attachment/type s/Str})

(s/defschema BlacklistEntry
  {:blacklist/user UserWithAttributes
   :blacklist/resource {:resource/ext-id s/Str}})

(s/defschema Blacklist
  [BlacklistEntry])

(s/defschema Handler
  (assoc UserWithAttributes
         (s/optional-key :handler/active?) s/Bool))

(s/defschema Permissions
  #{(apply s/enum (conj commands/command-names :see-everything))})

(s/defschema Application
  {:application/id s/Int
   :application/external-id (rjs/field
                             s/Str
                             {:description "Assigned external id if it exists, otherwise the generated one"})
   (s/optional-key :application/assigned-external-id) s/Str
   (s/optional-key :application/generated-external-id) s/Str
   :application/state s/Keyword
   :application/todo (s/maybe (s/enum :new-application
                                      :no-pending-requests
                                      :resubmitted-application
                                      :waiting-for-decision
                                      :waiting-for-review
                                      :waiting-for-your-decision
                                      :waiting-for-your-review))
   :application/created DateTime
   :application/modified DateTime
   (s/optional-key :application/first-submitted) DateTime
   (s/optional-key :application/deadline) DateTime
   (s/optional-key :application/copied-from) {:application/id s/Int
                                              :application/external-id s/Str}
   (s/optional-key :application/copied-to) [{:application/id s/Int
                                             :application/external-id s/Str}]
   :application/last-activity DateTime
   :application/applicant UserWithAttributes
   :application/members #{UserWithAttributes}
   :application/invited-members #{{:name s/Str
                                   :email s/Str}}
   (s/optional-key :application/blacklist) (rjs/field
                                            Blacklist
                                            {:description "Which members of this application are blacklisted for which resources"})
   :application/resources [V2Resource]
   :application/licenses [V2License]
   :application/accepted-licenses (s/maybe {UserId #{s/Int}})
   :application/events [Event]
   :application/description s/Str
   :application/forms [Form]
   :application/workflow {:workflow/id s/Int
                          :workflow/type s/Keyword
                          (s/optional-key :workflow.dynamic/handlers) [Handler]}
   :application/roles #{s/Keyword}
   :application/permissions Permissions
   :application/attachments [ApplicationAttachment]
   ;; TODO :application/end instead?
   (s/optional-key :entitlement/end) (s/maybe DateTime)})

(s/defschema ApplicationRaw
  (-> Application
      (dissoc :application/permissions
              :application/roles)
      (assoc :application/role-permissions {s/Keyword #{s/Keyword}}
             :application/user-roles {s/Str #{s/Keyword}})))

(s/defschema ApplicationOverview
  (dissoc Application
          :application/events
          :application/forms
          :application/licenses))
