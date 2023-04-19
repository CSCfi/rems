(ns rems.application.commands
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set]
            [medley.core :refer [assoc-some distinct-by update-existing]]
            [rems.common.application-util :as application-util]
            [rems.common.form :as form]
            [rems.common.util :refer [build-index]]
            [rems.form-validation :as form-validation]
            [rems.permissions :as permissions]
            [rems.schema-base :as schema-base]
            [rems.util :refer [assert-ex getx getx-in try-catch-ex]]
            [schema-refined.core :as r]
            [schema.core :as s]
            [clj-time.core :as time])
  (:import [java.util UUID]
           [org.joda.time DateTime]))

;;; Schemas

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
         :last-activity DateTime
         :expires-on DateTime))
(s/defschema SubmitCommand
  CommandBase)
(s/defschema DeleteCommand
  CommandBase)
(s/defschema UninviteMemberCommand
  (assoc CommandWithComment
         :member {:name s/Str
                  :email s/Str}))

(def command-schemas
  {:application.command/accept-invitation AcceptInvitationCommand
   :application.command/accept-licenses AcceptLicensesCommand
   :application.command/add-licenses AddLicensesCommand
   :application.command/add-member AddMemberCommand
   :application.command/approve ApproveCommand
   :application.command/assign-external-id AssignExternalIdCommand
   :application.command/change-applicant ChangeApplicantCommand
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
   :application.command/submit SubmitCommand
   :application.command/uninvite-member UninviteMemberCommand})

(def command-names
  (keys command-schemas))

(def commands-with-comments
  (set (for [[command schema] command-schemas
             :when (contains? schema (s/optional-key :comment))]
         command)))

(s/defschema Command
  (merge (apply r/StructDispatch :type (flatten (seq command-schemas)))
         CommandInternal))

(def ^:private validate-command-schema
  (s/validator Command))

(defn- validate-command [cmd]
  (assert-ex (contains? command-schemas (:type cmd))
             {:error {:type ::unknown-type}
              :value cmd})
  (validate-command-schema cmd))

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
  (is (= (set command-names)
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

(defn- disabled-catalogue-items-error [ids injections]
  (let [errors (for [id ids
                     :let [item ((getx injections :get-catalogue-item) id)]
                     :when (or (not (getx item :enabled))
                               (getx item :archived) ; TODO is this correct? besides, doesn't archived imply disabled?
                               (getx item :expired))]
                 {:type :disabled-catalogue-item :catalogue-item-id (getx item :id)})]
    (when (seq errors)
      {:errors (vec errors)})))

(defn- licenses-not-accepted-error [application userid]
  (when-not (application-util/accepted-licenses? application userid)
    {:errors [{:type :t.actions.errors/licenses-not-accepted}]}))

(defn- invalid-catalogue-items
  "Checks the given catalogue items for validity and merges the errors"
  [catalogue-item-ids injections]
  (apply merge-with into (keep #(invalid-catalogue-item-error % injections) catalogue-item-ids)))

(defn- changes-original-workflow
  "Checks that the given catalogue items are compatible with the original application from where the workflow is from. Applicant can't do it."
  [application catalogue-item-ids actor {:keys [get-catalogue-item]}]
  (let [catalogue-items (map get-catalogue-item catalogue-item-ids)
        original-workflow-id (get-in application [:application/workflow :workflow/id])
        new-workflow-ids (mapv :wfid catalogue-items)]
    (when (and (not (application-util/is-handler? application actor))
               (apply not= original-workflow-id new-workflow-ids))
      {:errors [{:type :changes-original-workflow :workflow/id original-workflow-id :ids new-workflow-ids}]})))

(defn- unbundlable-catalogue-items
  "Checks that the given catalogue items are bundlable."
  [catalogue-item-ids {:keys [get-catalogue-item]}]
  (let [catalogue-items (map get-catalogue-item catalogue-item-ids)]
    (when-not (= 1 (count (set (map :wfid catalogue-items))))
      {:errors [{:type :unbundlable-catalogue-items :catalogue-item-ids catalogue-item-ids}]})))

(defn- unbundlable-catalogue-items-for-actor
  "Checks that the given catalogue items are bundlable by the given actor."
  [application catalogue-item-ids actor injections]
  (when-not (application-util/is-handler? application actor)
    (unbundlable-catalogue-items catalogue-item-ids injections)))

(defn- form-validation-warnings [forms]
  (let [warnings (for [form forms
                       warning (form-validation/validate-fields (:form/fields form))]
                   (assoc warning :form-id (:form/id form)))]
    (when (seq warnings)
      {:warnings warnings})))

(defn- form-validation-errors [forms]
  (let [errors (for [form forms
                     error (form-validation/validate-fields (:form/fields form))]
                 (assoc error :form-id (:form/id form)))]
    (when (seq errors)
      {:errors errors})))

(defn- member? [userid application]
  (some #(= userid (:userid %))
        (application-util/applicant-and-members application)))

(defn already-member-error [application userid]
  (when (member? userid application)
    {:errors [{:type :t.actions.errors/already-member :userid userid :application-id (:application/id application)}]}))

(defn- ok-with-data [data events]
  (assoc data :events events))

(defn- ok [& events]
  (ok-with-data nil events))

(defn- invalid-attachment? [attachment cmd]
  (or (nil? attachment)
      (not= (:application/id attachment) (:application-id cmd))
      (not= (:attachment/user attachment) (:actor cmd))))

(defn- invalid-attachments-error [cmd injections]
  (let [invalid-ids (for [att (:attachments cmd)
                          :let [id (:attachment/id att)
                                get-attachment-metadata (getx injections :get-attachment-metadata)]
                          :when (invalid-attachment? (get-attachment-metadata id) cmd)]
                      id)]
    (when (seq invalid-ids)
      {:errors [{:type :invalid-attachments
                 :attachments (sort invalid-ids)}]})))

(defn- add-comment-and-attachments [cmd _application injections event]
  (or (invalid-attachments-error cmd injections)
      (ok (assoc-some event
                      :application/comment (:comment cmd)
                      :event/attachments (when-let [att (:attachments cmd)]
                                           (vec att))))))

(defn- build-forms-list [workflow catalogue-item-ids {:keys [get-catalogue-item]}]
  (->> catalogue-item-ids
       (mapv get-catalogue-item)
       (mapv :formid)
       (remove nil?)
       (mapv (fn [form-id] {:form/id form-id}))
       (concat (get-in workflow [:workflow :forms])) ; NB: workflow forms end up first
       (distinct-by :form/id)))

(defn- build-resources-list [catalogue-item-ids {:keys [get-catalogue-item]}]
  (->> catalogue-item-ids
       (mapv get-catalogue-item)
       (mapv (fn [catalogue-item]
               {:catalogue-item/id (:id catalogue-item)
                :resource/ext-id (:resid catalogue-item)}))))

(defn- build-licenses-list [catalogue-item-ids {:keys [get-catalogue-item-licenses]}]
  (->> catalogue-item-ids
       (mapcat get-catalogue-item-licenses)
       (distinct-by :license/id)
       (mapv #(select-keys % [:license/id]))))

(defn- entitlement-end-not-in-future-error
  "Checks that entitlement date time, if provided, is in the future."
  [dt]
  (when dt
    (when-not (time/after? dt (time/today-at 23 59 59))
      {:errors [{:type :t.actions.errors/entitlement-end-not-in-future}]})))

(defn- application-created-event! [{:keys [catalogue-item-ids time actor] :as cmd}
                                   {:keys [allocate-application-ids! get-catalogue-item get-workflow]
                                    :as injections}]
  (or (must-not-be-empty cmd :catalogue-item-ids)
      (invalid-catalogue-items catalogue-item-ids injections)
      (unbundlable-catalogue-items catalogue-item-ids injections)
      (disabled-catalogue-items-error catalogue-item-ids injections)
      (let [workflow-id (-> (first catalogue-item-ids)
                            get-catalogue-item
                            :wfid)
            workflow (get-workflow workflow-id)
            workflow-type (getx-in workflow [:workflow :type])
            ids (allocate-application-ids! time)]
        {:event {:event/type :application.event/created
                 :event/time time
                 :event/actor actor
                 :application/id (:application/id ids)
                 :application/external-id (:application/external-id ids)
                 :application/resources (build-resources-list catalogue-item-ids injections)
                 :application/licenses (build-licenses-list catalogue-item-ids injections)
                 :application/forms (build-forms-list workflow catalogue-item-ids injections)
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

(defn validate-application [application field-values]
  (let [forms (for [form (:application/forms application)]
                (-> form
                    (form/enrich-form-answers field-values nil)
                    (form/enrich-form-field-visible)))]
    (merge (form-validation-errors forms)
           (form-validation-warnings forms))))

(defmethod command-handler :application.command/save-draft
  [cmd application _injections]
  (let [answers (:field-values cmd)
        forms (for [form (:application/forms application)]
                (-> form
                    (form/enrich-form-answers answers nil)
                    (form/enrich-form-field-visible)))
        visible-values (for [form forms
                             field (:form/fields form)
                             :when (:field/visible field)]
                         {:form (:form/id form) :field (:field/id field) :value (:field/value field)})]
    (ok-with-data (form-validation-warnings forms)
                  (list (-> {:event/type :application.event/draft-saved
                             :application/field-values visible-values}
                            (assoc-some :application/duo-codes (:duo-codes cmd)))))))

(defmethod command-handler :application.command/accept-licenses
  [cmd _application _injections]
  (ok {:event/type :application.event/licenses-accepted
       :application/accepted-licenses (set (:accepted-licenses cmd))}))

(defmethod command-handler :application.command/submit
  [cmd application _injections]
  (or (merge-with concat
                  (licenses-not-accepted-error application (:actor cmd))
                  (form-validation-errors (:application/forms application)))
      (ok {:event/type :application.event/submitted})))

(defmethod command-handler :application.command/approve
  [cmd application injections]
  (or (entitlement-end-not-in-future-error (:entitlement-end cmd))
      (add-comment-and-attachments cmd application injections
                                   (merge {:event/type :application.event/approved}
                                          (when-let [end (:entitlement-end cmd)]
                                            {:entitlement/end end})))))

(defn- empty-redact-attachments-error [cmd]
  (when (empty? (:redacted-attachments cmd))
    {:errors [{:type :empty-redact-attachments}]}))

(defn- invalid-redact-attachments-error [cmd application]
  (let [redacted-ids (set (map :attachment/id (:redacted-attachments cmd)))
        attachment-ids (set (map :attachment/id (:application/attachments application)))]
    (when-some [invalid-ids (seq (clojure.set/difference redacted-ids attachment-ids))]
      {:errors [{:type :invalid-redact-attachments
                 :attachments (sort invalid-ids)}]})))

(defn- forbidden-redact-attachments-error [cmd application]
  (let [redacted-ids (set (map :attachment/id (:redacted-attachments cmd)))
        roles (permissions/user-roles application (:actor cmd))
        attachments (build-index {:keys [:attachment/id]} (:application/attachments application))
        forbidden-ids (->> redacted-ids
                           (keep #(get attachments %))
                           (remove #(application-util/can-redact-attachment % roles (:actor cmd)))
                           (map :attachment/id))]
    (when (seq forbidden-ids)
      {:errors [{:type :forbidden-redact-attachments
                 :attachments (sort forbidden-ids)}]})))

(defmethod command-handler :application.command/redact-attachments
  [cmd application injections]
  (or (empty-redact-attachments-error cmd)
      (invalid-redact-attachments-error cmd application)
      (forbidden-redact-attachments-error cmd application)
      (add-comment-and-attachments cmd application injections
                                   {:event/type :application.event/attachments-redacted
                                    :event/redacted-attachments (vec (:redacted-attachments cmd))
                                    :application/public (:public cmd)})))

(defmethod command-handler :application.command/reject
  [cmd application injections]
  (add-comment-and-attachments cmd application injections
                               {:event/type :application.event/rejected}))

(defmethod command-handler :application.command/return
  [cmd application injections]
  (add-comment-and-attachments cmd application injections
                               {:event/type :application.event/returned}))

(defmethod command-handler :application.command/close
  [cmd application injections]
  (add-comment-and-attachments cmd application injections
                               {:event/type :application.event/closed}))

(defmethod command-handler :application.command/revoke
  [cmd application injections]
  (add-comment-and-attachments cmd application injections
                               {:event/type :application.event/revoked}))

(defmethod command-handler :application.command/request-decision
  [cmd application injections]
  (or (must-not-be-empty cmd :deciders)
      (invalid-users-errors (:deciders cmd) injections)
      (add-comment-and-attachments cmd application injections
                                   {:event/type :application.event/decision-requested
                                    :application/request-id (UUID/randomUUID)
                                    :application/deciders (:deciders cmd)})))

(defn- actor-is-not-decider-error [application cmd]
  (when-not (contains? (get application :rems.application.model/latest-decision-request-by-user)
                       (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod command-handler :application.command/decide
  [cmd application injections]
  (or (actor-is-not-decider-error application cmd)
      (when-not (contains? #{:approved :rejected} (:decision cmd))
        {:errors [{:type :invalid-decision :decision (:decision cmd)}]})
      (let [last-request-for-actor (get-in application [:rems.application.model/latest-decision-request-by-user (:actor cmd)])]
        (add-comment-and-attachments cmd application injections
                                     {:event/type :application.event/decided
                                      :application/request-id last-request-for-actor
                                      :application/decision (:decision cmd)}))))

(defmethod command-handler :application.command/request-review
  [cmd application injections]
  (or (must-not-be-empty cmd :reviewers)
      (invalid-users-errors (:reviewers cmd) injections)
      (add-comment-and-attachments cmd application injections
                                   {:event/type :application.event/review-requested
                                    :application/request-id (UUID/randomUUID)
                                    :application/reviewers (:reviewers cmd)})))

(defn- actor-is-not-reviewer-error [application cmd]
  (when-not (contains? (get application :rems.application.model/latest-review-request-by-user)
                       (:actor cmd))
    {:errors [{:type :forbidden}]}))

(defmethod command-handler :application.command/review
  [cmd application injections]
  (or (actor-is-not-reviewer-error application cmd)
      (let [last-request-for-actor (get-in application [:rems.application.model/latest-review-request-by-user (:actor cmd)])]
        (add-comment-and-attachments cmd application injections
                                     {:event/type :application.event/reviewed
                                      ;; Currently we want to tie all comments to the latest request.
                                      ;; In the future this might change so that commenters can freely continue to comment
                                      ;; on any request they have gotten.
                                      :application/request-id last-request-for-actor}))))

(defmethod command-handler :application.command/remark
  [cmd application injections]
  (add-comment-and-attachments cmd application injections
                               {:event/type :application.event/remarked
                                :application/public (:public cmd)}))

(defmethod command-handler :application.command/add-licenses
  [cmd application injections]
  (or (must-not-be-empty cmd :licenses)
      (add-comment-and-attachments cmd application injections
                                   {:event/type :application.event/licenses-added
                                    :application/licenses (mapv (fn [id] {:license/id id}) (:licenses cmd))})))

(defmethod command-handler :application.command/change-resources
  [cmd application {:keys [get-catalogue-item get-workflow] :as injections}]
  (let [cat-ids (:catalogue-item-ids cmd)
        workflow (when (seq cat-ids) (get-workflow (-> (first cat-ids) get-catalogue-item :wfid)))]
    (or (must-not-be-empty cmd :catalogue-item-ids)
        (invalid-catalogue-items cat-ids injections)
        (unbundlable-catalogue-items-for-actor application cat-ids (:actor cmd) injections)
        (changes-original-workflow application cat-ids (:actor cmd) injections)
        (add-comment-and-attachments cmd application injections
                                     {:event/type :application.event/resources-changed
                                      :application/forms (build-forms-list workflow cat-ids injections)
                                      :application/resources (build-resources-list cat-ids injections)
                                      :application/licenses (build-licenses-list cat-ids injections)}))))

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

(defmethod command-handler :application.command/invite-decider
  [cmd application injections]
  (add-comment-and-attachments cmd application injections
                               {:event/type :application.event/decider-invited
                                :application/decider (:decider cmd)
                                :invitation/token ((getx injections :secure-token))}))

(defmethod command-handler :application.command/invite-reviewer
  [cmd application injections]
  (add-comment-and-attachments cmd application injections
                               {:event/type :application.event/reviewer-invited
                                :application/reviewer (:reviewer cmd)
                                :invitation/token ((getx injections :secure-token))}))

(defmethod command-handler :application.command/accept-invitation
  [cmd application _injections]
  (let [token (:token cmd)
        invitation (get-in application [:application/invitation-tokens token])]
    (cond
      (:application/member invitation)
      (or (already-member-error application (:actor cmd))
          (ok-with-data {:application-id (:application-id cmd)}
                        [{:event/type :application.event/member-joined
                          :application/id (:application-id cmd)
                          :invitation/token (:token cmd)}]))

      (:application/reviewer invitation)
      (ok-with-data {:application-id (:application-id cmd)}
                    [{:event/type :application.event/reviewer-joined
                      :application/id (:application-id cmd)
                      :invitation/token (:token cmd)
                      :application/request-id (UUID/randomUUID)}])

      (:application/decider invitation)
      (ok-with-data {:application-id (:application-id cmd)}
                    [{:event/type :application.event/decider-joined
                      :application/id (:application-id cmd)
                      :invitation/token (:token cmd)
                      :application/request-id (UUID/randomUUID)}])

      :else
      {:errors [{:type :t.actions.errors/invalid-token :token token}]})))

(defmethod command-handler :application.command/remove-member
  [cmd application injections]
  (or (when (= (:userid (:application/applicant application)) (:userid (:member cmd)))
        {:errors [{:type :cannot-remove-applicant}]})
      (when-not (member? (:userid (:member cmd)) application)
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (add-comment-and-attachments cmd application injections
                                   {:event/type :application.event/member-removed
                                    :application/member (:member cmd)})))

(defmethod command-handler :application.command/uninvite-member
  [cmd application injections]
  (or (when-not (contains? (set (map :application/member
                                     (vals (:application/invitation-tokens application))))
                           (:member cmd))
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (add-comment-and-attachments cmd application injections
                                   {:event/type :application.event/member-uninvited
                                    :application/member (:member cmd)})))

(defmethod command-handler :application.command/change-applicant
  [cmd application injections]
  (or (when-not (contains? (set (map :userid (:application/members application)))
                           (:userid (:member cmd)))
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (add-comment-and-attachments cmd application injections
                                   {:event/type :application.event/applicant-changed
                                    :application/applicant (:member cmd)})))

(defn- copy-field-values! [copy-attachment! application new-application-id]
  (vec
   (for [form (:application/forms application)
         field (:form/fields form)
         :let [value (:field/value field)]]
     {:form (:form/id form)
      :field (:field/id field)
      :value (cond
               (not= :attachment (:field/type field))
               value

               (empty? value)
               ""

               :else
               (->> (form/parse-attachment-ids value)
                    (mapv (partial copy-attachment! new-application-id))
                    form/unparse-attachment-ids))})))

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
            new-app-id (:application/id created-event)
            values (copy-field-values! (getx injections :copy-attachment!) application new-app-id)
            events (concat [created-event
                            {:event/type :application.event/draft-saved
                             :application/id new-app-id
                             :application/field-values values}]
                           ;; tracking copied applications applies to submitted applications only
                           (when-not (= :application.state/draft (:application/state application))
                             [{:event/type :application.event/copied-from
                               :application/id new-app-id
                               :application/copied-from (select-keys application [:application/id :application/external-id])}
                              {:event/type :application.event/copied-to
                               :application/id old-app-id
                               :application/copied-to (select-keys created-event [:application/id :application/external-id])}]))]
        (ok-with-data {:application-id new-app-id} events)))))

(defmethod command-handler :application.command/assign-external-id
  [cmd _application _injections]
  (ok {:event/type :application.event/external-id-assigned
       :application/external-id (:external-id cmd)}))

(defn- forbidden-error [application cmd]
  (let [permissions (if application
                      (permissions/user-permissions application (:actor cmd))
                      #{:application.command/create})]
    (when-not (contains? permissions (:type cmd))
      {:errors [{:type :forbidden}]})))

(defmethod command-handler :application.command/delete
  [cmd application injections]
  (ok {:event/type :application.event/deleted}))

(defn- invalid-expiration-error [cmd]
  (when (time/before? (:expires-on cmd) (:last-activity cmd))
    {:error [{:type :invalid-expiration}]}))

(defmethod command-handler :application.command/send-expiration-notifications
  [cmd _application _injections]
  (or (invalid-expiration-error cmd)
      (ok {:event/type :application.event/expiration-notifications-sent
           :last-activity (:last-activity cmd)
           :expires-on (:expires-on cmd)})))

(defn- add-common-event-fields-from-command [event cmd]
  (-> event
      (update :application/id (fn [app-id]
                                (or app-id (:application-id cmd))))
      (assoc :event/time (:time cmd))
      (update :event/actor #(or % (:actor cmd)))))

(defn- finalize-events [result cmd]
  (update-existing result :events (fn [events]
                                    (mapv #(add-common-event-fields-from-command % cmd) events))))

(defn- application-not-found-error [application cmd]
  (when (and (:application-id cmd) (not application))
    {:errors [{:type :application-not-found}]}))

(defn- find-users [cmd injections]
  ;; actor is handled already in middleware
  (case (:type cmd)
    (:application.command/add-member :application.command/remove-member :application.command/change-applicant)
    (update-in cmd [:member :userid] (getx injections :find-userid))

    :application.command/request-review
    (update cmd :reviewers #(mapv (getx injections :find-userid) %))

    :application.command/request-decision
    (update cmd :deciders #(mapv (getx injections :find-userid) %))

    cmd))

(defn handle-command [cmd application injections]
  (validate-command cmd) ; this is here mostly for tests, commands via the api are validated by compojure-api
  (or (invalid-user-error (:actor cmd) injections)
      (application-not-found-error application cmd)
      (let [result (-> cmd
                       (find-users injections)
                       (command-handler application injections)
                       (finalize-events cmd))]
        (or (when (:errors result) result) ;; prefer more specific errors
            (forbidden-error application cmd)
            result))))
