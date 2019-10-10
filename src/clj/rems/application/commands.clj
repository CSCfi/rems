(ns rems.application.commands
  (:require [clojure.test :refer :all]
            [rems.application-util :as application-util]
            [rems.application.model :as model]
            [rems.permissions :as permissions]
            [rems.util :refer [getx getx-in assert-ex try-catch-ex]]
            [schema-refined.core :as r]
            [schema.core :as s])
  (:import [clojure.lang ExceptionInfo]
           [java.util UUID]
           [org.joda.time DateTime]))

;;; Schemas

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema CommandInternal
  {:type s/Keyword
   :actor UserId
   :time DateTime})

(s/defschema CommandBase
  {:application-id s/Int})

(s/defschema AcceptInvitationCommand
  (assoc CommandBase
         :token s/Str))
(s/defschema AcceptLicensesCommand
  (assoc CommandBase
         :accepted-licenses [s/Int]))
(s/defschema AddLicensesCommand
  (assoc CommandBase
         :comment s/Str
         :licenses [s/Int]))
(s/defschema AddMemberCommand
  (assoc CommandBase
         :member {:userid UserId}))
(s/defschema ApproveCommand
  (assoc CommandBase
         :comment s/Str))
(s/defschema ChangeResourcesCommand
  (assoc CommandBase
         (s/optional-key :comment) s/Str
         :catalogue-item-ids [s/Int]))
(s/defschema CloseCommand
  (assoc CommandBase
         :comment s/Str))
(s/defschema CommentCommand
  (assoc CommandBase
         :comment s/Str))
(s/defschema CopyAsNewCommand
  CommandBase)
(s/defschema CreateCommand
  {:catalogue-item-ids [s/Int]})
(s/defschema DecideCommand
  (assoc CommandBase
         :decision (s/enum :approved :rejected)
         :comment s/Str))
(s/defschema InviteMemberCommand
  (assoc CommandBase
         :member {:name s/Str
                  :email s/Str}))
(s/defschema RejectCommand
  (assoc CommandBase
         :comment s/Str))
(s/defschema RemarkCommand
  (assoc CommandBase
         :comment s/Str
         :public s/Bool))
(s/defschema RemoveMemberCommand
  (assoc CommandBase
         :member {:userid UserId}
         :comment s/Str))
;; TODO RequestComment/Comment could be renamed to RequestReview/Review to be in line with the UI
(s/defschema RequestCommentCommand
  (assoc CommandBase
         :commenters [UserId]
         :comment s/Str))
(s/defschema RequestDecisionCommand
  (assoc CommandBase
         :deciders [UserId]
         :comment s/Str))
(s/defschema ReturnCommand
  (assoc CommandBase
         :comment s/Str))
(s/defschema RevokeCommand
  (assoc CommandBase
         :comment s/Str))
(s/defschema SaveDraftCommand
  (assoc CommandBase
         ;; {s/Int s/Str} is what we want, but that isn't nicely representable as JSON
         :field-values [{:field s/Int
                         :value s/Str}]))
(s/defschema SubmitCommand
  CommandBase)
(s/defschema UninviteMemberCommand
  (assoc CommandBase
         :member {:name s/Str
                  :email s/Str}
         :comment s/Str))

(def command-schemas
  {:application.command/accept-invitation AcceptInvitationCommand
   :application.command/accept-licenses AcceptLicensesCommand
   :application.command/add-licenses AddLicensesCommand
   :application.command/add-member AddMemberCommand
   :application.command/approve ApproveCommand
   :application.command/change-resources ChangeResourcesCommand
   :application.command/close CloseCommand
   :application.command/comment CommentCommand
   :application.command/copy-as-new CopyAsNewCommand
   :application.command/create CreateCommand
   :application.command/decide DecideCommand
   :application.command/invite-member InviteMemberCommand
   :application.command/reject RejectCommand
   :application.command/remark RemarkCommand
   :application.command/remove-member RemoveMemberCommand
   :application.command/request-comment RequestCommentCommand
   :application.command/request-decision RequestDecisionCommand
   :application.command/return ReturnCommand
   :application.command/revoke RevokeCommand
   :application.command/save-draft SaveDraftCommand
   :application.command/submit SubmitCommand
   :application.command/uninvite-member UninviteMemberCommand})

(s/defschema Command
  (merge (apply r/StructDispatch :type (flatten (seq command-schemas)))
         CommandInternal))

(defn- validate-command [cmd]
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

;;; Command handlers

(defmulti command-handler
  "Receives a command and produces events."
  (fn [cmd _application _injections] (:type cmd)))

(deftest test-all-command-types-handled
  (is (= (set (keys command-schemas))
         (set (keys (methods command-handler))))))

(defn- must-not-be-empty [cmd key]
  (when-not (seq (get cmd key))
    {:errors [{:type :must-not-be-empty :key key}]}))

(defn- invalid-user-error [user-id {:keys [valid-user?]}]
  (cond
    (not valid-user?) {:errors [{:type :missing-injection :injection :valid-user?}]}
    (not (valid-user? user-id)) {:errors [{:type :t.form.validation/invalid-user :userid user-id}]}))

(defn- invalid-users-errors
  "Checks the given users for validity and merges the errors"
  [user-ids injections]
  (apply merge-with into (keep #(invalid-user-error % injections) user-ids)))

(defn- invalid-catalogue-item-error [catalogue-item-id {:keys [get-catalogue-item]}]
  (cond
    (not get-catalogue-item) {:errors [{:type :missing-injection :injection :get-catalogue-item}]}
    (not (get-catalogue-item catalogue-item-id)) {:errors [{:type :invalid-catalogue-item :catalogue-item-id catalogue-item-id}]}))

(defn- disabled-catalogue-items-error [application {:keys [get-catalogue-item]}]
  (let [errors (for [item (:application/resources application)
                     :when (or (not (getx item :catalogue-item/enabled))
                               (getx item :catalogue-item/archived)
                               (getx item :catalogue-item/expired))]
                 {:type :t.actions.errors/disabled-catalogue-item :catalogue-item-id (getx item :catalogue-item/id)})]
    (when (not (empty? errors))
      {:errors (vec errors)})))

(defn- licenses-not-accepted-error [application userid]
  (when-not (application-util/accepted-licenses? application userid)
    {:errors [{:type :t.actions.errors/licenses-not-accepted}]}))

(defn- invalid-catalogue-items
  "Checks the given catalogue items for validity and merges the errors"
  [catalogue-item-ids injections]
  (apply merge-with into (keep #(invalid-catalogue-item-error % injections) catalogue-item-ids)))

(defn- workflow-handlers [application]
  (set (mapv :userid (get-in application [:application/workflow :workflow.dynamic/handlers]))))

(defn- is-handler? [application user]
  (contains? (workflow-handlers application) user))

(defn- changes-original-workflow
  "Checks that the given catalogue items are compatible with the original application from where the workflow is from. Applicant can't do it."
  [application catalogue-item-ids actor {:keys [get-catalogue-item]}]
  (let [catalogue-items (map get-catalogue-item catalogue-item-ids)
        original-workflow-id (get-in application [:application/workflow :workflow/id])
        new-workflow-ids (mapv :wfid catalogue-items)]
    (when (and (not (is-handler? application actor))
               (apply not= original-workflow-id new-workflow-ids))
      {:errors [{:type :changes-original-workflow :workflow/id original-workflow-id :ids new-workflow-ids}]})))

(defn- changes-original-form
  "Checks that the given catalogue items are compatible with the original application from where the form is from. Applicant can't do it."
  [application catalogue-item-ids actor {:keys [get-catalogue-item]}]
  (let [catalogue-items (map get-catalogue-item catalogue-item-ids)
        original-form-id (get-in application [:application/form :form/id])
        new-form-ids (mapv :formid catalogue-items)]
    (when (and (not (is-handler? application actor))
               (apply not= original-form-id new-form-ids))
      {:errors [{:type :changes-original-form :form/id original-form-id :ids new-form-ids}]})))

(defn- unbundlable-catalogue-items
  "Checks that the given catalogue items are bundlable."
  [catalogue-item-ids {:keys [get-catalogue-item]}]
  (let [catalogue-items (map get-catalogue-item catalogue-item-ids)]
    (when (not= 1
                (count (set (map :formid catalogue-items)))
                (count (set (map :wfid catalogue-items))))
      {:errors [{:type :unbundlable-catalogue-items :catalogue-item-ids catalogue-item-ids}]})))

(defn- unbundlable-catalogue-items-for-actor
  "Checks that the given catalogue items are bundlable by the given actor."
  [application catalogue-item-ids actor injections]
  (when-not (is-handler? application actor)
    (unbundlable-catalogue-items catalogue-item-ids injections)))

(defn- validation-error [application {:keys [validate-fields]}]
  (let [errors (validate-fields (getx-in application [:application/form :form/fields]))]
    (when (seq errors)
      {:errors errors})))

(defn- valid-invitation-token? [application token]
  (contains? (:application/invitation-tokens application) token))

(defn- invitation-token-error [application token]
  (when-not (valid-invitation-token? application token)
    {:errors [{:type :t.actions.errors/invalid-token :token token}]}))

(defn- member? [userid application]
  (some #(= userid (:userid %))
        (application-util/applicant-and-members application)))

(defn already-member-error [application userid]
  (when (member? userid application)
    {:errors [{:type :already-member :application-id (:application/id application)}]}))

(defn- ok-with-data [data events]
  (assoc data :events events))

(defn- ok [& events]
  (ok-with-data nil events))

(defn- build-resources-list [catalogue-item-ids {:keys [get-catalogue-item]}]
  (->> catalogue-item-ids
       (mapv get-catalogue-item)
       (mapv (fn [catalogue-item]
               {:catalogue-item/id (:id catalogue-item)
                :resource/ext-id (:resid catalogue-item)}))))

(defn- build-licenses-list [catalogue-item-ids {:keys [get-catalogue-item-licenses]}]
  (->> catalogue-item-ids
       (mapcat get-catalogue-item-licenses)
       distinct
       (mapv (fn [license]
               {:license/id (:id license)}))))

(defn- application-created-event! [{:keys [catalogue-item-ids time actor] :as cmd}
                                   {:keys [allocate-application-ids! get-catalogue-item get-workflow]
                                    :as injections}]
  (or (must-not-be-empty cmd :catalogue-item-ids)
      (invalid-catalogue-items catalogue-item-ids injections)
      (unbundlable-catalogue-items catalogue-item-ids injections)
      (let [items (map get-catalogue-item catalogue-item-ids)
            form-id (:formid (first items))
            workflow-id (:wfid (first items))
            workflow-type (:type (:workflow (get-workflow workflow-id)))
            _ (assert (= :workflow/dynamic workflow-type) {:workflow-type workflow-type}) ; TODO: support other workflows
            ids (allocate-application-ids! time)]
        {:event {:event/type :application.event/created
                 :event/time time
                 :event/actor actor
                 :application/id (:application/id ids)
                 :application/external-id (:application/external-id ids)
                 :application/resources (build-resources-list catalogue-item-ids injections)
                 :application/licenses (build-licenses-list catalogue-item-ids injections)
                 :form/id form-id
                 :workflow/id workflow-id
                 :workflow/type workflow-type}})))

(defmethod command-handler :application.command/create
  [cmd application injections]
  ;; XXX: handle-command will execute this method even when the permission for it is missing,
  ;;      so we need to guard against that to avoid the mutative operation of allocating
  ;;      new application IDs when the application already exists.
  (when (nil? application)
    (let [created-event-or-errors (application-created-event! cmd injections)]
      (if (:errors created-event-or-errors)
        created-event-or-errors
        (let [event (:event created-event-or-errors)]
          (ok-with-data {:application-id (:application/id event)}
                        [event]))))))

(defmethod command-handler :application.command/save-draft
  [cmd _application _injections]
  (ok {:event/type :application.event/draft-saved
       :application/field-values (into {}
                                       (for [{:keys [field value]} (:field-values cmd)]
                                         [field value]))}))

(defmethod command-handler :application.command/accept-licenses
  [cmd _application _injections]
  (ok {:event/type :application.event/licenses-accepted
       :application/accepted-licenses (set (:accepted-licenses cmd))}))

(defmethod command-handler :application.command/submit
  [cmd application injections]
  (or (merge-with concat
                  (disabled-catalogue-items-error application injections)
                  (licenses-not-accepted-error application (:actor cmd))
                  (validation-error application injections))
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

(defmethod command-handler :application.command/revoke
  [cmd application {:keys [add-to-blacklist!]}]
  (doseq [resource (:application/resources application)]
    (doseq [user (application-util/applicant-and-members application)]
      (add-to-blacklist! {:user (:userid user)
                          :resource (:resource/ext-id resource)
                          :actor (:actor cmd)
                          :comment (:comment cmd)})))
  (ok {:event/type :application.event/revoked
       :application/comment (:comment cmd)}))

(defmethod command-handler :application.command/request-decision
  [cmd _application injections]
  (or (must-not-be-empty cmd :deciders)
      (invalid-users-errors (:deciders cmd) injections)
      (ok {:event/type :application.event/decision-requested
           :application/request-id (UUID/randomUUID)
           :application/deciders (:deciders cmd)
           :application/comment (:comment cmd)})))

(defn- actor-is-not-decider-error [application cmd]
  (when-not (contains? (get application ::model/latest-decision-request-by-user)
                       (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod command-handler :application.command/decide
  [cmd application _injections]
  (or (actor-is-not-decider-error application cmd)
      (when-not (contains? #{:approved :rejected} (:decision cmd))
        {:errors [{:type :invalid-decision :decision (:decision cmd)}]})
      (let [last-request-for-actor (get-in application [::model/latest-decision-request-by-user (:actor cmd)])]
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
  (when-not (contains? (get application ::model/latest-comment-request-by-user)
                       (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod command-handler :application.command/comment
  [cmd application _injections]
  (or (actor-is-not-commenter-error application cmd)
      (let [last-request-for-actor (get-in application [::model/latest-comment-request-by-user (:actor cmd)])]
        (ok {:event/type :application.event/commented
             ;; Currently we want to tie all comments to the latest request.
             ;; In the future this might change so that commenters can freely continue to comment
             ;; on any request they have gotten.
             :application/request-id last-request-for-actor
             :application/comment (:comment cmd)}))))

(defmethod command-handler :application.command/remark
  [cmd _application _injections]
  (ok {:event/type :application.event/remarked
       :application/comment (:comment cmd)
       :application/public (:public cmd)}))

(defmethod command-handler :application.command/add-licenses
  [cmd _application injections]
  (or (must-not-be-empty cmd :licenses)
      (ok {:event/type :application.event/licenses-added
           :application/licenses (mapv (fn [id] {:license/id id}) (:licenses cmd))
           :application/comment (:comment cmd)})))

(defmethod command-handler :application.command/change-resources
  [cmd application injections]
  (or (must-not-be-empty cmd :catalogue-item-ids)
      (invalid-catalogue-items (:catalogue-item-ids cmd) injections)
      (unbundlable-catalogue-items-for-actor application (:catalogue-item-ids cmd) (:actor cmd) injections)
      (changes-original-form application (:catalogue-item-ids cmd) (:actor cmd) injections)
      (changes-original-workflow application (:catalogue-item-ids cmd) (:actor cmd) injections)
      (ok (merge {:event/type :application.event/resources-changed
                  :application/resources (build-resources-list (:catalogue-item-ids cmd) injections)
                  :application/licenses (build-licenses-list (:catalogue-item-ids cmd) injections)}
                 (when (:comment cmd)
                   {:application/comment (:comment cmd)})))))

(defmethod command-handler :application.command/add-member
  [cmd application injections]
  (or (invalid-user-error (:userid (:member cmd)) injections)
      (already-member-error application (:userid (:member cmd)))
      (ok {:event/type :application.event/member-added
           :application/member (:member cmd)})))

(defmethod command-handler :application.command/invite-member
  [cmd _application {:keys [secure-token]}]
  (ok {:event/type :application.event/member-invited
       :application/member (:member cmd)
       :invitation/token (secure-token)}))

(defmethod command-handler :application.command/accept-invitation
  [cmd application _injections]
  (or (already-member-error application (:actor cmd))
      (invitation-token-error application (:token cmd))
      (ok-with-data {:application-id (:application-id cmd)}
                    [{:event/type :application.event/member-joined
                      :application/id (:application-id cmd)
                      :invitation/token (:token cmd)}])))

(defmethod command-handler :application.command/remove-member
  [cmd application _injections]
  (or (when (= (:userid (:application/applicant application)) (:userid (:member cmd)))
        {:errors [{:type :cannot-remove-applicant}]})
      (when-not (member? (:userid (:member cmd)) application)
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

(defmethod command-handler :application.command/copy-as-new
  [cmd application injections]
  (let [catalogue-item-ids (map :catalogue-item/id (:application/resources application))
        created-event-or-errors (application-created-event! {:catalogue-item-ids catalogue-item-ids
                                                             :time (:time cmd)
                                                             :actor (:actor cmd)}
                                                            injections)]
    (if (:errors created-event-or-errors)
      created-event-or-errors
      (let [created-event (:event created-event-or-errors)
            old-app-id (:application/id application)
            new-app-id (:application/id created-event)]
        (ok-with-data
         {:application-id new-app-id}
         [created-event
          {:event/type :application.event/draft-saved
           :application/id new-app-id
           :application/field-values (->> (:form/fields (:application/form application))
                                          (map (fn [field]
                                                 [(:field/id field) (:field/value field)]))
                                          (into {}))}
          {:event/type :application.event/copied-from
           :application/id new-app-id
           :application/copied-from (select-keys application [:application/id :application/external-id])}
          {:event/type :application.event/copied-to
           :application/id old-app-id
           :application/copied-to (select-keys created-event [:application/id :application/external-id])}])))))

(defn- add-common-event-fields-from-command [event cmd]
  (-> event
      (update :application/id (fn [app-id]
                                (or app-id (:application-id cmd))))
      (assoc :event/time (:time cmd)
             :event/actor (:actor cmd))))

(defn- enrich-result [result cmd]
  (if (:events result)
    (update result :events (fn [events]
                             (mapv #(add-common-event-fields-from-command % cmd) events)))
    result))

(defn ^:dynamic postprocess-command-result-for-tests [result _cmd _application]
  result)

(defn handle-command [cmd application injections]
  (validate-command cmd) ; this is here mostly for tests, commands via the api are validated by compojure-api
  (let [permissions (if application
                      (permissions/user-permissions application (:actor cmd))
                      #{:application.command/create})]
    (if (contains? permissions (:type cmd))
      (-> (command-handler cmd application injections)
          (enrich-result cmd)
          (postprocess-command-result-for-tests cmd application))
      {:errors (or (:errors (command-handler cmd application injections)) ; prefer more specific error
                   [{:type :forbidden}])})))

(deftest test-handle-command
  (let [application (model/application-view nil {:event/type :application.event/created
                                                 :event/actor "applicant"
                                                 :workflow/type :workflow/dynamic})
        command {:application-id 123 :time (DateTime. 1000)
                 :type :application.command/save-draft
                 :field-values []
                 :actor "applicant"}]
    (testing "executes command when user is authorized"
      (is (not (:errors (handle-command command application {})))))
    (testing "fails when command fails validation"
      (is (thrown-with-msg? ExceptionInfo #"Value does not match schema"
                            (handle-command (assoc command :time 3) application {}))))
    (testing "fails when user is not authorized"
      ;; the permission checks should happen before executing the command handler
      ;; and only depend on the roles and permissions
      (let [application (permissions/remove-role-from-user application :applicant "applicant")
            result (handle-command command application {})]
        (is (= {:errors [{:type :forbidden}]} result))))))
