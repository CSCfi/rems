(ns rems.application.master-workflow
  "The master workflow is a superset of all possible commands and who is
  allowed to execute them. Workflows for production use can be derived from
  the master workflow by restricting the possible commands (permissions)."
  (:require [rems.application.commands :as commands]
            [rems.permissions :as permissions]))

(def whitelist
  (permissions/compile-rules
   (into [{:permission :see-everything}]
         (map (fn [command]
                {:permission command})
              (sort (keys commands/command-schemas))))))

(defmulti calculate-permissions
  (fn [_application event] (:event/type event)))

(defmethod calculate-permissions :default
  [application _event]
  application)

(def ^:private submittable-application-commands
  #{:application.command/save-draft
    :application.command/submit
    :application.command/close
    :application.command/remove-member
    :application.command/invite-member
    :application.command/uninvite-member
    :application.command/accept-licenses
    :application.command/change-resources
    :application.command/copy-as-new})

(def ^:private non-submittable-application-commands
  #{:application.command/remove-member
    :application.command/uninvite-member
    :application.command/accept-licenses
    :application.command/copy-as-new})

(def ^:private handler-all-commands
  #{:application.command/remark
    :application.command/add-licenses
    :application.command/add-member
    :application.command/assign-external-id
    :application.command/change-resources
    :application.command/remove-member
    :application.command/invite-member
    :application.command/uninvite-member
    :application.command/request-comment
    :application.command/request-decision
    :application.command/return
    :application.command/approve
    :application.command/reject
    :application.command/close})

(def ^:private handler-returned-commands
  (disj handler-all-commands
        :application.command/return
        :application.command/approve
        :application.command/reject
        :application.command/request-decision))

(def ^:private created-permissions
  {:applicant submittable-application-commands
   :member #{:application.command/accept-licenses
             :application.command/copy-as-new}
   :reporter #{:see-everything}
   ;; member before accepting an invitation
   :everyone-else #{:application.command/accept-invitation}})

(def ^:private submitted-permissions
  {:applicant non-submittable-application-commands
   :handler (conj handler-all-commands :see-everything)
   :commenter #{:see-everything
                :application.command/remark
                :application.command/comment}
   :past-commenter #{:see-everything
                     :application.command/remark}
   :decider #{:see-everything
              :application.command/remark
              :application.command/decide
              :application.command/approve
              :application.command/reject}
   :past-decider #{:see-everything
                   :application.command/remark}})

(def ^:private returned-permissions
  {:applicant submittable-application-commands
   :handler (conj handler-returned-commands :see-everything)
   :decider #{:see-everything
              :application.command/remark
              :application.command/decide}})

(def ^:private approved-permissions
  {:applicant non-submittable-application-commands
   :handler #{:see-everything
              :application.command/remark
              :application.command/add-member
              :application.command/change-resources
              :application.command/remove-member
              :application.command/invite-member
              :application.command/uninvite-member
              :application.command/close
              :application.command/revoke}
   :decider #{:see-everything
              :application.command/remark
              :application.command/decide}})

(def ^:private closed-permissions
  {:applicant #{:application.command/copy-as-new}
   :member #{:application.command/copy-as-new}
   :handler #{:see-everything
              :application.command/remark}
   :commenter #{:see-everything}
   :past-commenter #{:see-everything}
   :decider #{:see-everything}
   :past-decider #{:see-everything}
   :everyone-else #{}})

(defmethod calculate-permissions :application.event/created
  [application event]
  (-> application
      (permissions/give-role-to-users :applicant [(:event/actor event)])
      (permissions/update-role-permissions created-permissions)))

(defmethod calculate-permissions :application.event/member-added
  [application event]
  (-> application
      (permissions/give-role-to-users :member [(get-in event [:application/member :userid])])))

(defmethod calculate-permissions :application.event/member-joined
  [application event]
  (-> application
      (permissions/give-role-to-users :member [(:event/actor event)])))

(defmethod calculate-permissions :application.event/member-removed
  [application event]
  (-> application
      (permissions/remove-role-from-user :member (get-in event [:application/member :userid]))))

(defmethod calculate-permissions :application.event/submitted
  [application _event]
  (-> application
      (permissions/update-role-permissions submitted-permissions)))

(defmethod calculate-permissions :application.event/returned
  [application _event]
  (-> application
      (permissions/update-role-permissions returned-permissions)))

(defmethod calculate-permissions :application.event/comment-requested
  [application event]
  (-> application
      (permissions/give-role-to-users :commenter (:application/commenters event))))

(defmethod calculate-permissions :application.event/commented
  [application event]
  (-> application
      (permissions/remove-role-from-user :commenter (:event/actor event))
      (permissions/give-role-to-users :past-commenter [(:event/actor event)]))) ; allow to still view the application

(defmethod calculate-permissions :application.event/decision-requested
  [application event]
  (-> application
      (permissions/give-role-to-users :decider (:application/deciders event))))

(defmethod calculate-permissions :application.event/decided
  [application event]
  (-> application
      (permissions/remove-role-from-user :decider (:event/actor event))
      (permissions/give-role-to-users :past-decider [(:event/actor event)]))) ; allow to still view the application

(defmethod calculate-permissions :application.event/approved
  [application _event]
  (-> application
      (permissions/update-role-permissions approved-permissions)))

(defmethod calculate-permissions :application.event/rejected
  [application _event]
  (-> application
      (permissions/update-role-permissions closed-permissions)))

(defmethod calculate-permissions :application.event/closed
  [application _event]
  (-> application
      (permissions/update-role-permissions closed-permissions)))

(defmethod calculate-permissions :application.event/revoked
  [application _event]
  (-> application
      (permissions/update-role-permissions closed-permissions)))
