(ns rems.application.schema
  (:require [rems.schema-base :as schema-base]
            [schema-refined.core :as r]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CommandInternal
  {:type s/Keyword
   :actor schema-base/UserId
   :time DateTime})

(s/defschema CommandBase
  {:application-id s/Int})

(s/defschema CommandAttachment
  {:attachment/id s/Int})

(s/defschema CommandWithComment
  (assoc CommandBase
         (s/optional-key :comment) s/Str
         (s/optional-key :attachments) [CommandAttachment]))

(s/defschema AcceptInvitationCommand
  (assoc CommandBase
         :token s/Str))

(s/defschema AcceptLicensesCommand
  (assoc CommandBase
         :accepted-licenses [s/Int]))

(s/defschema AddLicensesCommand
  (assoc CommandWithComment
         :licenses [s/Int]))

(s/defschema AddMemberCommand
  (assoc CommandBase
         :member schema-base/User))

(s/defschema ApproveCommand
  (assoc CommandWithComment
         (s/optional-key :entitlement-end) DateTime))

(s/defschema AssignExternalIdCommand
  (assoc CommandBase
         :external-id s/Str))

(s/defschema ChangeProcessingStateCommand
  (assoc CommandWithComment
         :processing-state s/Str
         :public s/Bool))

(s/defschema ChangeResourcesCommand
  (assoc CommandWithComment
         :catalogue-item-ids [s/Int]))

(s/defschema CloseCommand
  CommandWithComment)

(s/defschema CopyAsNewCommand
  CommandBase)

(s/defschema CreateCommand
  {:catalogue-item-ids [s/Int]})

(s/defschema DecideCommand
  (assoc CommandWithComment
         :decision (s/enum :approved :rejected)))

(s/defschema InviteReviewerCommand
  (assoc CommandWithComment
         :reviewer {:name s/Str
                    :email s/Str}))

(s/defschema InviteDeciderCommand
  (assoc CommandWithComment
         :decider {:name s/Str
                   :email s/Str}))

(s/defschema InviteMemberCommand
  (assoc CommandBase
         :member {:name s/Str
                  :email s/Str}))

(s/defschema ChangeApplicantCommand
  (assoc CommandWithComment
         :member schema-base/User))

(s/defschema RejectCommand
  CommandWithComment)

(s/defschema RedactAttachmentsCommand
  (assoc CommandWithComment
         :redacted-attachments [CommandAttachment]
         :public s/Bool))

(s/defschema RemarkCommand
  (assoc CommandWithComment
         :public s/Bool))

(s/defschema RemoveMemberCommand
  (assoc CommandWithComment
         :member schema-base/User))

(s/defschema RequestReviewCommand
  (assoc CommandWithComment
         :reviewers [schema-base/UserId]))

(s/defschema RequestDecisionCommand
  (assoc CommandWithComment
         :deciders [schema-base/UserId]))

(s/defschema ReturnCommand
  CommandWithComment)

(s/defschema ReviewCommand
  CommandWithComment)

(s/defschema RevokeCommand
  CommandWithComment)

(s/defschema SaveDraftCommand
  (assoc CommandBase
         :field-values [{:form schema-base/FormId
                         :field schema-base/FieldId
                         :value schema-base/FieldValue}]
         (s/optional-key :duo-codes) [schema-base/DuoCode]))

(s/defschema SendExpirationNotificationsCommand
  (assoc CommandBase
         :expires-on DateTime))

(s/defschema SoftDeleteCommand
  CommandBase)

(s/defschema SubmitCommand
  CommandBase)

(s/defschema DeleteCommand
  (assoc CommandBase
         (s/optional-key :expires-on) DateTime))

(s/defschema UninviteMemberCommand
  (assoc CommandWithComment
         :member {:name s/Str
                  :email s/Str}))

(s/defschema VoteCommand
  (assoc CommandWithComment
         :vote s/Str))

(def command-schemas
  {:application.command/accept-invitation AcceptInvitationCommand
   :application.command/accept-licenses AcceptLicensesCommand
   :application.command/add-licenses AddLicensesCommand
   :application.command/add-member AddMemberCommand
   :application.command/approve ApproveCommand
   :application.command/assign-external-id AssignExternalIdCommand
   :application.command/change-applicant ChangeApplicantCommand
   :application.command/change-processing-state ChangeProcessingStateCommand
   :application.command/change-resources ChangeResourcesCommand
   :application.command/close CloseCommand
   :application.command/copy-as-new CopyAsNewCommand
   :application.command/create CreateCommand
   :application.command/decide DecideCommand
   :application.command/delete DeleteCommand
   :application.command/invite-decider InviteDeciderCommand
   :application.command/invite-member InviteMemberCommand
   :application.command/invite-reviewer InviteReviewerCommand
   :application.command/redact-attachments RedactAttachmentsCommand
   :application.command/reject RejectCommand
   :application.command/remark RemarkCommand
   :application.command/remove-member RemoveMemberCommand
   :application.command/request-decision RequestDecisionCommand
   :application.command/request-review RequestReviewCommand
   :application.command/return ReturnCommand
   :application.command/review ReviewCommand
   :application.command/revoke RevokeCommand
   :application.command/save-draft SaveDraftCommand
   :application.command/send-expiration-notifications SendExpirationNotificationsCommand
   :application.command/soft-delete SoftDeleteCommand
   :application.command/submit SubmitCommand
   :application.command/uninvite-member UninviteMemberCommand
   :application.command/vote VoteCommand})

(def command-names
  (keys command-schemas))

(def commands-with-comments
  (set (for [[command schema] command-schemas
             :when (contains? schema (s/optional-key :comment))]
         command)))

(s/defschema Command
  (merge (apply r/StructDispatch :type (flatten (seq command-schemas)))
         CommandInternal))
