(ns rems.application.commands
  (:require [clojure.test :refer :all]
            [rems.util :refer [getx]]
            [schema.core :as s]
            [schema-refined.core :as r]
            [rems.util :refer [assert-ex try-catch-ex]])
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
         ;; {s/Num s/Str} is what we want, but that isn't nicely representable as JSON
         :field-values [{:field s/Num
                         :value s/Str}]))

(s/defschema AcceptLicensesCommand
  (assoc CommandBase
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

(s/defschema AddLicensesCommand
  (assoc CommandBase
         :comment s/Str
         :licenses [s/Num]))

(s/defschema AddMemberCommand
  (assoc CommandBase
         :member {:userid UserId}))

(s/defschema ChangeResourcesCommand
  (assoc CommandBase
         (s/optional-key :comment) s/Str
         :catalogue-item-ids [s/Num]))

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
   :application.command/add-licenses AddLicensesCommand
   :application.command/add-member AddMemberCommand
   :application.command/change-resources ChangeResourcesCommand
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

(s/defschema Command
  (merge (apply r/StructDispatch :type (flatten (seq command-schemas)))
         CommandInternal))

(defn validate-command [cmd]
  (assert-ex (contains? command-schemas (:type cmd)) {:error {:type ::unknown-type}
                                                      :value cmd})
  (s/validate Command cmd))

(deftest test-validate-command
  (testing "check specific command schema"
    (is (validate-command {:application-id 42
                           :type :application.command/submit
                           :time (DateTime.)
                           :actor "applicant"})))
  (testing "missing event specific key"
    (is (= {:actor 'missing-required-key}
           (:error
            (try-catch-ex
             (validate-command {:application-id 42
                                :type :application.command/submit
                                :time (DateTime.)}))))))
  (testing "unknown event type"
    (is (= {:type ::unknown-type}
           (:error
            (try-catch-ex
             (validate-command
              {:type :does-not-exist})))))))
