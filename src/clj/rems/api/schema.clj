(ns rems.api.schema
  "Shared schema definitions for the API"
  (:require [rems.application.commands :as commands]
            [rems.schema-base :as schema-base]
            [ring.swagger.json-schema :as rjs]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

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
   :formid schema-base/FormId
   (s/optional-key :form-name) s/Str
   :resid s/Str
   :resource-id s/Int
   :organization schema-base/OrganizationOverview
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
   :organization schema-base/OrganizationOverview
   :enabled s/Bool
   :archived s/Bool
   :localizations {s/Keyword {:title s/Str
                              :textcontent s/Str
                              (s/optional-key :attachment-id) (s/maybe s/Int)}}})

(s/defschema Licenses
  [License])

(s/defschema ResourceLicense License)

(s/defschema Event
  (assoc schema-base/EventBase
         :event/actor-attributes schema-base/UserWithAttributes
         s/Keyword s/Any))

(s/defschema Entitlement
  {:resource s/Str
   :user schema-base/UserWithAttributes
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
  (merge schema-base/OrganizationId
         {:enabled s/Bool}))

(s/defschema OrganizationArchivedCommand
  (merge schema-base/OrganizationId
         {:archived s/Bool}))

(s/defschema SuccessResponse
  {:success s/Bool
   (s/optional-key :errors) [s/Any]})

(s/defschema V2Resource
  {:resource/id s/Int
   :resource/ext-id s/Str
   :catalogue-item/id s/Int
   :catalogue-item/title schema-base/LocalizedString
   :catalogue-item/infourl schema-base/LocalizedString
   :catalogue-item/start DateTime
   :catalogue-item/end (s/maybe DateTime)
   :catalogue-item/enabled s/Bool
   :catalogue-item/expired s/Bool
   :catalogue-item/archived s/Bool})

(s/defschema V2License
  {:license/id s/Int
   :license/type (s/enum :text :link :attachment)
   :license/title schema-base/LocalizedString
   (s/optional-key :license/link) schema-base/LocalizedString
   (s/optional-key :license/text) schema-base/LocalizedString
   (s/optional-key :license/attachment-id) schema-base/LocalizedInt
   (s/optional-key :license/attachment-filename) schema-base/LocalizedString
   :license/enabled s/Bool
   :license/archived s/Bool})

(s/defschema Workflow
  {:id s/Int
   :organization schema-base/OrganizationOverview
   :owneruserid schema-base/UserId
   :modifieruserid schema-base/UserId
   :title s/Str
   :workflow s/Any
   :licenses [License]
   :enabled s/Bool
   :archived s/Bool})

(def not-neg? (partial <= 0))

;;; template for a form field, before answering
(s/defschema FieldTemplate
  {:field/id schema-base/FieldId
   :field/type (s/enum :attachment :date :description :email :header :label :multiselect :option :phonenumber :text :texta :table )
   :field/title schema-base/LocalizedString
   (s/optional-key :field/placeholder) schema-base/LocalizedString
   :field/optional s/Bool
   (s/optional-key :field/options) [{:key s/Str
                                     :label schema-base/LocalizedString}]
   (s/optional-key :field/columns) [{:key s/Str
                                     :label schema-base/LocalizedString}]
   (s/optional-key :field/max-length) (s/maybe (s/constrained s/Int not-neg?))
   (s/optional-key :field/privacy) (rjs/field
                                    (s/enum :public :private)
                                    {:description "Public by default"})
   (s/optional-key :field/visibility) (rjs/field
                                       {:visibility/type (s/enum :always :only-if)
                                        (s/optional-key :visibility/field) {:field/id schema-base/FieldId}
                                        (s/optional-key :visibility/values) [s/Str]}
                                       {:description "Always visible by default"})
   (s/optional-key :field/info-text) schema-base/LocalizedString})

(s/defschema NewFieldTemplate
  (-> FieldTemplate
      (dissoc :field/id)
      (assoc (s/optional-key :field/id) schema-base/FieldId)))

(s/defschema Field
  (assoc FieldTemplate
         :field/value schema-base/FieldValue
         :field/visible s/Bool
         :field/private s/Bool
         (s/optional-key :field/previous-value) schema-base/FieldValue))

(s/defschema FormTemplate
  {:form/id s/Int
   :organization schema-base/OrganizationOverview
   (s/optional-key :form/title) (rjs/field s/Str
                                           {:deprecate true
                                            :description "DEPRECATED, will disappear, use either internal name or external title as you need"})
   :form/internal-name (rjs/field s/Str
                                  {:description "The internal name of the form only visible to the administration."})
   :form/external-title (rjs/field schema-base/LocalizedString
                                   {:description "The title of the form used publicly in the application."})
   :form/fields [FieldTemplate]
   (s/optional-key :form/errors) (s/maybe {(s/optional-key :organization) s/Any
                                           (s/optional-key :form/title) s/Any
                                           (s/optional-key :form/internal-name) s/Any
                                           (s/optional-key :form/external-title) s/Any
                                           (s/optional-key :form/fields) {s/Num s/Any}})
   ;; TODO: rename the following to use :status/ namespace (also in all other entities)
   :enabled s/Bool
   :archived s/Bool})

(s/defschema FormTemplateOverview
  (dissoc FormTemplate :form/fields))

;;; instance for form template once filled in by user
(s/defschema Form
  {:form/id s/Int
   (s/optional-key :form/title) (rjs/field s/Str
                                           {:deprecate true
                                            :description "DEPRECATED, will disappear, use either internal name or external title as you need"})
   :form/internal-name s/Str
   :form/external-title schema-base/LocalizedString
   :form/fields [Field]})

(s/defschema ApplicationAttachment
  {:attachment/id s/Int
   :attachment/filename s/Str
   :attachment/type s/Str})

(s/defschema BlacklistEntry
  {:blacklist/user schema-base/UserWithAttributes
   :blacklist/resource {:resource/ext-id s/Str}})

(s/defschema Blacklist
  [BlacklistEntry])

(s/defschema Handler
  (assoc schema-base/UserWithAttributes
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
   :application/applicant schema-base/UserWithAttributes
   :application/members #{schema-base/UserWithAttributes}
   :application/invited-members #{{:name s/Str
                                   :email s/Str}}
   (s/optional-key :application/blacklist) (rjs/field
                                            Blacklist
                                            {:description "Which members of this application are blacklisted for which resources"})
   :application/resources [V2Resource]
   :application/licenses [V2License]
   :application/accepted-licenses (s/maybe {schema-base/UserId #{s/Int}})
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
