(ns rems.application.commands
  (:require [rems.util :refer [getx]]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema CommandInternal
  {:type s/Keyword
   :actor UserId
   :time DateTime})

(s/defschema CommandBase
  {:application-id s/Int})

(s/defschema SaveDraftCommand
  (assoc CommandBase
         :field-values {s/Num s/Str}
         :accepted-licenses #{s/Num}))

(s/defschema AcceptLicensesCommand
  (assoc CommandBase
         :type (s/enum :application.command/accept-licenses)
         :accepted-licenses s/Any))

(s/defschema SubmitCommand
  CommandBase)

(s/defschema ApproveCommand
  (assoc CommandBase
         :comment s/Str))

(s/defschema RejectCommand
  (assoc CommandBase
         :comment s/Str))

(s/defschema ReturnCommand
  (assoc CommandBase
         :comment s/Str))

(s/defschema CloseCommand
  (assoc CommandBase
         :comment s/Str))

(s/defschema RequestDecisionCommand
  (assoc CommandBase
         :deciders [UserId]
         :comment s/Str))

(s/defschema DecideCommand
  (assoc CommandBase
         :decision (s/enum :approved :rejected)
         :comment s/Str))

(s/defschema RequestCommentCommand
  (assoc CommandBase
         :commenters [UserId]
         :comment s/Str))

(s/defschema CommentCommand
  (assoc CommandBase
         :comment s/Str))

(s/defschema AddMemberCommand
  (assoc CommandBase
         :member {:userid UserId}))

(s/defschema InviteMemberCommand
  (assoc CommandBase
         :member {:name s/Str
                  :email s/Str}))

(s/defschema AcceptInvitationCommand
  (assoc CommandBase
         :token s/Str))

(s/defschema RemoveMemberCommand
  (assoc CommandBase
         :member {:userid UserId}
         :comment s/Str))

(s/defschema UninviteMemberCommand
  (assoc CommandBase
         :member {:name s/Str
                  :email s/Str}
         :comment s/Str))

(def command-schemas
  {#_:application.command/require-license
   :application.command/accept-invitation AcceptInvitationCommand
   :application.command/accept-licenses AcceptLicensesCommand
   :application.command/add-member AddMemberCommand
   :application.command/invite-member InviteMemberCommand
   :application.command/approve ApproveCommand
   :application.command/close CloseCommand
   :application.command/comment CommentCommand
   :application.command/decide DecideCommand
   :application.command/reject RejectCommand
   :application.command/request-comment RequestCommentCommand
   :application.command/request-decision RequestDecisionCommand
   :application.command/remove-member RemoveMemberCommand
   :application.command/return ReturnCommand
   :application.command/save-draft SaveDraftCommand
   :application.command/submit SubmitCommand
   :application.command/uninvite-member UninviteMemberCommand
   #_:application.command/withdraw})

(defn validate-command [cmd]
  (let [type (:type cmd)
        schema (merge CommandInternal
                      (getx command-schemas type))]
    (s/validate schema cmd)))
