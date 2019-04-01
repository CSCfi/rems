(ns rems.workflow.dynamic
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [rems.application.commands :as commands]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.permissions :as permissions]
            [rems.util :refer [getx]])
  (:import (org.joda.time DateTime)))

;;; Application model

(defmulti ^:private apply-event
  "Applies an event to an application state."
  ;; dispatch by event type
  ;; TODO: the workflow parameter could be removed; this method is the only one to use it and it's included in application
  (fn [_application workflow event] [(:event/type event) (or (:type workflow)
                                                             (:workflow/type event))]))

(deftest test-all-event-types-handled
  (let [handled-event-types (map first (keys (methods apply-event)))]
    (is (= (set (keys events/event-schemas))
           (set handled-event-types)))))

(defmethod apply-event [:application.event/created :workflow/dynamic]
  [application _workflow event]
  (assoc application
         :state :application.state/draft
         :applicantuserid (:event/actor event)
         :members [{:userid (:event/actor event)}]
         :form/id (:form/id event)
         :application/licenses (:application/licenses event)
         :application/external-id (:application/external-id event)
         :workflow {:type (:workflow/type event)
                    :handlers (vec (:workflow.dynamic/handlers event))}))

(defmethod apply-event [:application.event/draft-saved :workflow/dynamic]
  [application _workflow event]
  (assoc application
         ::applicant-accepted-licenses (:application/accepted-licenses event)
         :form-contents {:items (:application/field-values event)
                         :licenses (->> (:application/accepted-licenses event)
                                        (map (fn [id] [id "approved"]))
                                        (into {}))
                         :accepted-licenses (->> (:accepted-licenses application)
                                                 (merge {(:event/actor event) (:application/accepted-licenses event)}))}))

(defmethod apply-event [:application.event/submitted :workflow/dynamic]
  [application _workflow event]
  (assoc application
         :state :application.state/submitted
         :commenters #{}
         :deciders #{}
         :previous-submitted-form-contents (:submitted-form-contents application)
         :submitted-form-contents (:form-contents application)))

(defmethod apply-event [:application.event/approved :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state :application.state/approved))

(defmethod apply-event [:application.event/rejected :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state :application.state/rejected))

(defmethod apply-event [:application.event/returned :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state :application.state/returned))

(defmethod apply-event [:application.event/closed :workflow/dynamic]
  [application _workflow _event]
  (assoc application :state :application.state/closed))

(defmethod apply-event [:application.event/decision-requested :workflow/dynamic]
  [application _workflow event]
  (-> application
      (update :deciders into (:application/deciders event))
      ;; TODO: keep ::latest-decision-request-by-user
      (update ::latest-decision-request-by-user merge (zipmap (:application/deciders event)
                                                              (repeat (:application/request-id event))))))

(defmethod apply-event [:application.event/decided :workflow/dynamic]
  [application _workflow event]
  ;; we don't store the decisions in the state, they're available via
  ;; the event list
  (-> application
      (update :deciders disj (:event/actor event))
      ;; TODO: keep ::latest-decision-request-by-user
      (update ::latest-decision-request-by-user dissoc (:event/actor event))))

(defmethod apply-event [:application.event/comment-requested :workflow/dynamic]
  [application _workflow event]
  (-> application
      (update :commenters into (:application/commenters event))
      ;; TODO: keep ::latest-comment-request-by-user
      (update ::latest-comment-request-by-user merge (zipmap (:application/commenters event)
                                                             (repeat (:application/request-id event))))))

(defmethod apply-event [:application.event/commented :workflow/dynamic]
  [application _workflow event]
  ;; we don't store the comments in the state, they're available via
  ;; the event list
  (-> application
      (update :commenters disj (:event/actor event))
      ;; TODO: keep ::latest-comment-request-by-user
      (update ::latest-comment-request-by-user dissoc (:event/actor event))))

(defmethod apply-event [:application.event/member-added :workflow/dynamic]
  [application _workflow event]
  (update application :members #(vec (conj % (:application/member event)))))

(defmethod apply-event [:application.event/member-invited :workflow/dynamic]
  [application _workflow event]
  (-> application
      (update :invited-members #(vec (conj % (:application/member event))))
      (update :invitation-tokens assoc (:invitation/token event) (:application/member event))))

(defmethod apply-event [:application.event/member-joined :workflow/dynamic]
  [application _workflow event]
  (let [member-by-token ((:invitation-tokens application) (:invitation/token event))]
    (-> application
        (update :members #(vec (conj % {:userid (:event/actor event)})))
        (update :invited-members #(remove #{member-by-token} %))
        (update :invitation-tokens dissoc (:invitation/token event)))))

(defmethod apply-event [:application.event/member-removed :workflow/dynamic]
  [application _workflow event]
  (update application :members #(vec (remove #{(:application/member event)} %))))

(defmethod apply-event [:application.event/member-uninvited :workflow/dynamic]
  [application _workflow event]
  (update application :invited-members #(vec (remove #{(:application/member event)} %))))

(defn apply-events [application events]
  ;; TODO: remove old apply-event
  (reduce (fn [application event] (-> (apply-event application (:workflow application) event)
                                      (model/application-view event)
                                      (model/calculate-permissions event)))
          application
          events))

(defn clean-internal-state [application]
  (dissoc application ::latest-comment-request-by-user ::latest-decision-request-by-user))

;;; Command handlers

(defmulti command-handler
  "Handles a command by an event."
  (fn [cmd _application _injections] (:type cmd)))

(defn get-command-types
  "Fetch sequence of supported command names."
  []
  (keys (methods command-handler)))

(deftest test-all-command-types-handled
  (is (= (set (keys commands/command-schemas)) (set (get-command-types)))))

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
  (contains? (:invitation-tokens application) token))

(defn- invitation-token-error [application token]
  (when-not (valid-invitation-token? application token)
    {:errors [{:type :t.actions.errors/invalid-token :token token}]}))

(defn already-member-error [application userid]
  (when (contains? (conj (set (map :userid (:application/members application)))
                         (:application/applicant application))
                   userid)
    {:errors [{:type :already-member :application-id (:id application)}]}))

(defn- ok [event]
  {:success true
   :result event})

(defmethod command-handler :application.command/save-draft
  [cmd _application _injections]
  (ok {:event/type :application.event/draft-saved
       :application/field-values (:field-values cmd)
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
           :application/request-id (java.util.UUID/randomUUID)
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
           :application/request-id (java.util.UUID/randomUUID)
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
  (or (when (= (:applicantuserid application) (:userid (:member cmd)))
        {:errors [{:type :cannot-remove-applicant}]})
      (when-not (contains? (set (map :userid (:members application)))
                           (:userid (:member cmd)))
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (ok {:event/type :application.event/member-removed
           :application/member (:member cmd)
           :application/comment (:comment cmd)})))

(defmethod command-handler :application.command/uninvite-member
  [cmd application _injections]
  (or (when-not (contains? (set (map (juxt :name :email) (:invited-members application)))
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
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Value does not match schema"
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
