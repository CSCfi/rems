(ns rems.application.commands
  (:require [rems.util :refer [getx]]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema CommandInternal
  {:actor UserId
   :time DateTime})

(s/defschema CommandBase
  {:application-id s/Int})

(s/defschema SaveDraftCommand
  (assoc CommandBase
         :type (s/enum :application.command/save-draft) ;; single-value enums are supported by swagger, unlike s/eq
         :field-values s/Any
         :accepted-licenses s/Any))

(s/defschema SubmitCommand
  (assoc CommandBase
         :type (s/enum :application.command/submit)))

(s/defschema ApproveCommand
  (assoc CommandBase
         :type (s/enum :application.command/approve)
         :comment s/Str))

(s/defschema RejectCommand
  (assoc CommandBase
         :type (s/enum :application.command/reject)
         :comment s/Str))

(s/defschema ReturnCommand
  (assoc CommandBase
         :type (s/enum :application.command/return)
         :comment s/Str))

(s/defschema CloseCommand
  (assoc CommandBase
         :type (s/enum :application.command/close)
         :comment s/Str))

(s/defschema RequestDecisionCommand
  (assoc CommandBase
         :type (s/enum :application.command/request-decision)
         :deciders [UserId]
         :comment s/Str))

(s/defschema DecideCommand
  (assoc CommandBase
         :type (s/enum :application.command/decide)
         :decision (s/enum :approved :rejected)
         :comment s/Str))

(s/defschema RequestCommentCommand
  (assoc CommandBase
         :type (s/enum :application.command/request-comment)
         :commenters [UserId]
         :comment s/Str))

(s/defschema CommentCommand
  (assoc CommandBase
         :type (s/enum :application.command/comment)
         :comment s/Str))

(s/defschema AddMemberCommand
  (assoc CommandBase
         :type (s/enum :application.command/add-member)
         :member {:userid UserId}))

(s/defschema InviteMemberCommand
  (assoc CommandBase
         :type (s/enum :application.command/invite-member)
         :member {:name s/Str
                  :email s/Str}))

(s/defschema AcceptInvitationCommand
  (assoc CommandBase
         :type (s/enum :application.command/accept-invitation)
         :token s/Str))

(s/defschema RemoveMemberCommand
  (assoc CommandBase
         :type (s/enum :application.command/remove-member)
         :member {:userid UserId}
         :comment s/Str))

(s/defschema UninviteMemberCommand
  (assoc CommandBase
         :type (s/enum :application.command/uninvite-member)
         :member {:name s/Str
                  :email s/Str}
         :comment s/Str))

(def command-schemas
  {#_:application.command/accept-license
   #_:application.command/require-license
   :application.command/accept-invitation AcceptInvitationCommand
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
  (s/validate (merge CommandInternal
                     (getx command-schemas (:type cmd)))
              cmd))
