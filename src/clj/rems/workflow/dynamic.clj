(ns rems.workflow.dynamic
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [rems.application.commands :as commands]
            [rems.application.model :as model]
            [rems.permissions :as permissions]
            [rems.util :refer [getx]])
  (:import [clojure.lang ExceptionInfo]
           [java.util UUID]
           [org.joda.time DateTime]))

;;; Application model

(defmulti ^:private write-model
  "Projection for application state which is only needed in the command handlers.
   We don't separate the read and write models in a strict CQRS way, but the
   command handlers reuse the read model for command validation, instead of
   having a dedicated write model."
  (fn [_application event] (:event/type event)))

(defmethod write-model :default
  [application _event]
  application)

(defmethod write-model :application.event/decision-requested
  [application event]
  (-> application
      (update ::latest-decision-request-by-user merge (zipmap (:application/deciders event)
                                                              (repeat (:application/request-id event))))))

(defmethod write-model :application.event/decided
  [application event]
  (-> application
      (update ::latest-decision-request-by-user dissoc (:event/actor event))))

(defmethod write-model :application.event/comment-requested
  [application event]
  (-> application
      (update ::latest-comment-request-by-user merge (zipmap (:application/commenters event)
                                                             (repeat (:application/request-id event))))))

(defmethod write-model :application.event/commented
  [application event]
  (-> application
      (update ::latest-comment-request-by-user dissoc (:event/actor event))))

(defn apply-events [application events]
  (reduce (fn [application event] (-> application
                                      (model/application-view event)
                                      (model/calculate-permissions event)
                                      (write-model event)))
          application
          events))

;;; Command handlers

(defmulti command-handler
  "Receives a command and produces events."
  (fn [cmd _application _injections] (:type cmd)))

(deftest test-all-command-types-handled
  (is (= (set (keys commands/command-schemas))
         (set (keys (methods command-handler))))))

(defn- invalid-user-error [user-id injections]
  (cond
    (not (:valid-user? injections)) {:errors [{:type :missing-injection :injection :valid-user?}]}
    (not ((:valid-user? injections) user-id)) {:errors [{:type :t.form.validation/invalid-user :userid user-id}]}))

(defn- invalid-users-errors
  "Checks the given users for validity and merges the errors"
  [user-ids injections]
  (apply merge-with into (keep #(invalid-user-error % injections) user-ids)))

(defn- validate-licenses [application]
  (let [all-licenses (set (map :license/id (:application/licenses application)))
        user-id (:application/applicant application)
        accepted-licenses (get-in application [:application/accepted-licenses user-id])
        missing-licenses (set/difference all-licenses accepted-licenses)]
    (->> (sort missing-licenses)
         (map (fn [license-id]
                {:type :t.form.validation/required
                 :license-id license-id})))))

(defn- validation-error [application {:keys [validate-form-answers]}]
  (let [form-id (:form/id application)
        answers (:rems.application.model/draft-answers application)
        errors (concat (validate-form-answers form-id {:items answers})
                       (validate-licenses application))]
    (when (seq errors)
      {:errors errors})))

(defn- valid-invitation-token? [application token]
  (contains? (:application/invitation-tokens application) token))

(defn- invitation-token-error [application token]
  (when-not (valid-invitation-token? application token)
    {:errors [{:type :t.actions.errors/invalid-token :token token}]}))

(defn- all-members [application]
  (conj (set (map :userid (:application/members application)))
        (:application/applicant application)))

(defn already-member-error [application userid]
  (when (contains? (all-members application) userid)
    {:errors [{:type :already-member :application-id (:id application)}]}))

(defn- ok [event]
  {:success true
   :result event})

(defmethod command-handler :application.command/save-draft
  [cmd _application _injections]
  (ok {:event/type :application.event/draft-saved
       :application/field-values (:field-values cmd)
       :application/accepted-licenses (set (:accepted-licenses cmd))}))

(defmethod command-handler :application.command/accept-licenses
  [cmd _application _injections]
  (ok {:event/type :application.event/accepted-licenses
       :application/accepted-licenses (set (:accepted-licenses cmd))}))

(defmethod command-handler :application.command/submit
  [_cmd application injections]
  (or (validation-error application injections)
      (ok {:event/type :application.event/submitted})))

(defmethod command-handler :application.command/approve
  [cmd _application _injections]
  (ok {:event/type :application.event/approved
       :application/comment (:comment cmd)}))

(defmethod command-handler :application.command/reject
  [cmd _application _injections]
  (ok {:event/type :application.event/rejected
       :application/comment (:comment cmd)}))

(defmethod command-handler :application.command/return
  [cmd _application _injections]
  (ok {:event/type :application.event/returned
       :application/comment (:comment cmd)}))

(defmethod command-handler :application.command/close
  [cmd _application _injections]
  (ok {:event/type :application.event/closed
       :application/comment (:comment cmd)}))

(defn- must-not-be-empty [cmd key]
  (when-not (seq (get cmd key))
    {:errors [{:type :must-not-be-empty :key key}]}))

(defmethod command-handler :application.command/request-decision
  [cmd _application injections]
  (or (must-not-be-empty cmd :deciders)
      (invalid-users-errors (:deciders cmd) injections)
      (ok {:event/type :application.event/decision-requested
           :application/request-id (UUID/randomUUID)
           :application/deciders (:deciders cmd)
           :application/comment (:comment cmd)})))

(defn- actor-is-not-decider-error [application cmd]
  (when-not (contains? (get-in application [:application/workflow :workflow.dynamic/awaiting-deciders])
                       (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod command-handler :application.command/decide
  [cmd application _injections]
  (or (actor-is-not-decider-error application cmd)
      (when-not (contains? #{:approved :rejected} (:decision cmd))
        {:errors [{:type :invalid-decision :decision (:decision cmd)}]})
      (let [last-request-for-actor (get-in application [::latest-decision-request-by-user (:actor cmd)])]
        (ok {:event/type :application.event/decided
             :application/request-id last-request-for-actor
             :application/decision (:decision cmd)
             :application/comment (:comment cmd)}))))

(defmethod command-handler :application.command/request-comment
  [cmd _application injections]
  (or (must-not-be-empty cmd :commenters)
      (invalid-users-errors (:commenters cmd) injections)
      (ok {:event/type :application.event/comment-requested
           :application/request-id (UUID/randomUUID)
           :application/commenters (:commenters cmd)
           :application/comment (:comment cmd)})))

(defn- actor-is-not-commenter-error [application cmd]
  (when-not (contains? (get-in application [:application/workflow :workflow.dynamic/awaiting-commenters])
                       (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod command-handler :application.command/comment
  [cmd application _injections]
  (or (actor-is-not-commenter-error application cmd)
      (let [last-request-for-actor (get-in application [::latest-comment-request-by-user (:actor cmd)])]
        (ok {:event/type :application.event/commented
             ;; Currently we want to tie all comments to the latest request.
             ;; In the future this might change so that commenters can freely continue to comment
             ;; on any request they have gotten.
             :application/request-id last-request-for-actor
             :application/comment (:comment cmd)}))))

(defmethod command-handler :application.command/add-member
  [cmd application injections]
  (or (invalid-user-error (:userid (:member cmd)) injections)
      (already-member-error application (:userid (:member cmd)))
      (ok {:event/type :application.event/member-added
           :application/member (:member cmd)})))

(defmethod command-handler :application.command/invite-member
  [cmd _application injections]
  (ok {:event/type :application.event/member-invited
       :application/member (:member cmd)
       :invitation/token ((getx injections :secure-token))}))

(defmethod command-handler :application.command/accept-invitation
  [cmd application injections]
  (or (invalid-user-error (:actor cmd) injections)
      (already-member-error application (:actor cmd))
      (invitation-token-error application (:token cmd))
      (ok {:event/type :application.event/member-joined
           :application/id (:application-id cmd)
           :invitation/token (:token cmd)})))

(defmethod command-handler :application.command/remove-member
  [cmd application _injections]
  (or (when (= (:application/applicant application) (:userid (:member cmd)))
        {:errors [{:type :cannot-remove-applicant}]})
      (when-not (contains? (all-members application) (:userid (:member cmd)))
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (ok {:event/type :application.event/member-removed
           :application/member (:member cmd)
           :application/comment (:comment cmd)})))

(defmethod command-handler :application.command/uninvite-member
  [cmd application _injections]
  (or (when-not (contains? (set (map (juxt :name :email)
                                     (vals (:application/invitation-tokens application))))
                           [(:name (:member cmd))
                            (:email (:member cmd))])
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (ok {:event/type :application.event/member-uninvited
           :application/member (:member cmd)
           :application/comment (:comment cmd)})))

(defn- add-common-event-fields-from-command [event cmd]
  (assoc event
         :event/time (:time cmd)
         :event/actor (:actor cmd)
         :application/id (:application-id cmd)))

(defn- enrich-result [result cmd]
  (if (:success result)
    (update result :result add-common-event-fields-from-command cmd)
    result))

(defn handle-command [cmd application injections]
  (commands/validate-command cmd) ;; this is here mostly for tests, commands via the api are validated by compojure-api
  (let [permissions (permissions/user-permissions application (:actor cmd))]
    (if (contains? permissions (:type cmd))
      (-> (command-handler cmd application injections)
          (enrich-result cmd))
      {:errors (or (:errors (command-handler cmd application injections)) ; prefer more specific error
                   [{:type :forbidden}])})))

(deftest test-handle-command
  (let [application (apply-events nil [{:event/type :application.event/created
                                        :event/actor "applicant"
                                        :workflow/type :workflow/dynamic
                                        :workflow.dynamic/handlers #{"assistant"}}])
        command {:application-id 123 :time (DateTime. 1000)
                 :type :application.command/save-draft
                 :field-values []
                 :accepted-licenses []
                 :actor "applicant"}]
    (testing "executes command when user is authorized"
      (is (:success (handle-command command application {}))))
    (testing "fails when command fails validation"
      (is (thrown-with-msg? ExceptionInfo #"Value does not match schema"
                            (handle-command (assoc command :time 3) application {}))))
    (testing "fails when user is not authorized"
      ;; the permission checks should happen before executing the command handler
      ;; and only depend on the roles and permissions
      (let [application (permissions/remove-role-from-user application :applicant "applicant")
            result (handle-command command application {})]
        (is (not (:success result)))
        (is (= [{:type :forbidden}] (:errors result)))))))

;;; Possible commands

(defn possible-commands
  "Returns the commands which the user is authorized to execute."
  [actor application-state]
  (permissions/user-permissions application-state actor))

(defn assoc-possible-commands [actor application-state]
  (assoc application-state
         :possible-commands (possible-commands actor application-state)))
