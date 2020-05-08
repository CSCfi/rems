(ns rems.application.commands
  (:require [clojure.test :refer [deftest is testing]]
            [medley.core :refer [assoc-some]]
            [rems.common.util :refer [build-index]]
            [rems.common.application-util :as application-util]
            [rems.permissions :as permissions]
            [rems.util :refer [assert-ex getx getx-in try-catch-ex update-present]]
            [schema-refined.core :as r]
            [schema.core :as s])
  (:import [java.util UUID]
           [org.joda.time DateTime]))

;;; Schemas

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)
(def FormId s/Int)
(def FieldId s/Str)

(s/defschema CommandInternal
  {:type s/Keyword
   :actor UserId
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
         :member {:userid UserId}))
(s/defschema ApproveCommand
  CommandWithComment)
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
(s/defschema InviteMemberCommand
  (assoc CommandBase
         :member {:name s/Str
                  :email s/Str}))
(s/defschema RejectCommand
  CommandWithComment)
(s/defschema RemarkCommand
  (assoc CommandWithComment
         :public s/Bool))
(s/defschema RemoveMemberCommand
  (assoc CommandWithComment
         :member {:userid UserId}))
(s/defschema RequestReviewCommand
  (assoc CommandWithComment
         :reviewers [UserId]))
(s/defschema RequestDecisionCommand
  (assoc CommandWithComment
         :deciders [UserId]))
(s/defschema ReturnCommand
  CommandWithComment)
(s/defschema ReviewCommand
  CommandWithComment)
(s/defschema RevokeCommand
  CommandWithComment)
(s/defschema SaveDraftCommand
  (assoc CommandBase
         :field-values [{:form FormId
                         :field FieldId
                         :value s/Str}]))
(s/defschema SubmitCommand
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
   :application.command/change-resources ChangeResourcesCommand
   :application.command/close CloseCommand
   :application.command/copy-as-new CopyAsNewCommand
   :application.command/create CreateCommand
   :application.command/decide DecideCommand
   :application.command/invite-member InviteMemberCommand
   :application.command/reject RejectCommand
   :application.command/remark RemarkCommand
   :application.command/remove-member RemoveMemberCommand
   :application.command/request-decision RequestDecisionCommand
   :application.command/request-review RequestReviewCommand
   :application.command/return ReturnCommand
   :application.command/review ReviewCommand
   :application.command/revoke RevokeCommand
   :application.command/save-draft SaveDraftCommand
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

(defn- disabled-catalogue-items-error [application]
  ;; resubmitting is fine even if catalogue item is disabled
  (when (= :application.state/draft (getx application :application/state))
    (let [errors (for [item (:application/resources application)
                       :when (or (not (getx item :catalogue-item/enabled))
                                 (getx item :catalogue-item/archived)
                                 (getx item :catalogue-item/expired))]
                   {:type :t.actions.errors/disabled-catalogue-item :catalogue-item-id (getx item :catalogue-item/id)})]
      (when (not (empty? errors))
        {:errors (vec errors)}))))

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

(defn- validation-error [application {:keys [validate-fields]}]
  (let [errors (for [form (:application/forms application)
                     error (validate-fields (:form/fields form))]
                 (assoc error :form-id (:form/id form)))]
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
    {:errors [{:type :t.actions.errors/already-member :userid userid :application-id (:application/id application)}]}))

(defn- ok-with-data [data events]
  (assoc data :events events))

(defn- ok [& events]
  (ok-with-data nil events))

(defn- invalid-attachments-error [injections cmd]
  (let [invalid-ids (for [att (:attachments cmd)
                          :let [id (:attachment/id att)
                                attachment ((getx injections :get-attachment-metadata) id)]
                          :when (or (nil? attachment)
                                    (not= (:attachment/user attachment) (:actor cmd))
                                    (not= (:application/id attachment) (:application-id cmd)))]
                      id)]
    (when (seq invalid-ids)
      {:errors [{:type :invalid-attachments :attachments invalid-ids}]})))

(defn- add-comment-and-attachments [cmd injections event]
  (or (invalid-attachments-error injections cmd)
      (ok (assoc-some event
                      :application/comment (:comment cmd)
                      :event/attachments (when-let [att (:attachments cmd)]
                                           (vec att))))))

(defn- build-forms-list [catalogue-item-ids {:keys [get-catalogue-item]}]
  (->> catalogue-item-ids
       (mapv get-catalogue-item)
       (mapv :formid)
       (distinct)
       (mapv (fn [form-id] {:form/id form-id}))))

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
            form-ids (distinct (map :formid items))
            workflow-id (:wfid (first items))
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
                 :application/forms (concat (get-in workflow [:workflow :forms])
                                            (mapv (fn [form-id] {:form/id form-id}) form-ids))
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
       :application/field-values (:field-values cmd)}))

(defmethod command-handler :application.command/accept-licenses
  [cmd _application _injections]
  (ok {:event/type :application.event/licenses-accepted
       :application/accepted-licenses (set (:accepted-licenses cmd))}))

(defmethod command-handler :application.command/submit
  [cmd application injections]
  (or (merge-with concat
                  (disabled-catalogue-items-error application)
                  (licenses-not-accepted-error application (:actor cmd))
                  (validation-error application injections))
      (ok {:event/type :application.event/submitted})))

(defmethod command-handler :application.command/approve
  [cmd application injections]
  (add-comment-and-attachments cmd injections
                               {:event/type :application.event/approved}))

(defmethod command-handler :application.command/reject
  [cmd application injections]
  (add-comment-and-attachments cmd injections
                               {:event/type :application.event/rejected}))

(defmethod command-handler :application.command/return
  [cmd application injections]
  (add-comment-and-attachments cmd injections
                               {:event/type :application.event/returned}))

(defmethod command-handler :application.command/close
  [cmd application injections]
  (add-comment-and-attachments cmd injections
                               {:event/type :application.event/closed}))

(defmethod command-handler :application.command/revoke
  [cmd application injections]
  (add-comment-and-attachments cmd injections
                               {:event/type :application.event/revoked}))

(defmethod command-handler :application.command/request-decision
  [cmd application injections]
  (or (must-not-be-empty cmd :deciders)
      (invalid-users-errors (:deciders cmd) injections)
      (add-comment-and-attachments cmd injections
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
        (add-comment-and-attachments cmd injections
                                     {:event/type :application.event/decided
                                      :application/request-id last-request-for-actor
                                      :application/decision (:decision cmd)}))))

(defmethod command-handler :application.command/request-review
  [cmd application injections]
  (or (must-not-be-empty cmd :reviewers)
      (invalid-users-errors (:reviewers cmd) injections)
      (add-comment-and-attachments cmd injections
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
        (add-comment-and-attachments cmd injections
                                     {:event/type :application.event/reviewed
                                      ;; Currently we want to tie all comments to the latest request.
                                      ;; In the future this might change so that commenters can freely continue to comment
                                      ;; on any request they have gotten.
                                      :application/request-id last-request-for-actor}))))

(defmethod command-handler :application.command/remark
  [cmd application injections]
  (add-comment-and-attachments cmd injections
                               {:event/type :application.event/remarked
                                :application/public (:public cmd)}))

(defmethod command-handler :application.command/add-licenses
  [cmd application injections]
  (or (must-not-be-empty cmd :licenses)
      (add-comment-and-attachments cmd injections
                                   {:event/type :application.event/licenses-added
                                    :application/licenses (mapv (fn [id] {:license/id id}) (:licenses cmd))})))

(defmethod command-handler :application.command/change-resources
  [cmd application injections]
  (let [cat-ids (:catalogue-item-ids cmd)]
    (or (must-not-be-empty cmd :catalogue-item-ids)
        (invalid-catalogue-items cat-ids injections)
        (unbundlable-catalogue-items-for-actor application cat-ids (:actor cmd) injections)
        (changes-original-workflow application cat-ids (:actor cmd) injections)
        (add-comment-and-attachments cmd injections
                                     {:event/type :application.event/resources-changed
                                      :application/forms (build-forms-list cat-ids injections)
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

(defmethod command-handler :application.command/accept-invitation
  [cmd application _injections]
  (or (already-member-error application (:actor cmd))
      (invitation-token-error application (:token cmd))
      (ok-with-data {:application-id (:application-id cmd)}
                    [{:event/type :application.event/member-joined
                      :application/id (:application-id cmd)
                      :invitation/token (:token cmd)}])))

(defmethod command-handler :application.command/remove-member
  [cmd application injections]
  (or (when (= (:userid (:application/applicant application)) (:userid (:member cmd)))
        {:errors [{:type :cannot-remove-applicant}]})
      (when-not (member? (:userid (:member cmd)) application)
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (add-comment-and-attachments cmd injections
                                   {:event/type :application.event/member-removed
                                    :application/member (:member cmd)})))

(defmethod command-handler :application.command/uninvite-member
  [cmd application injections]
  (or (when-not (contains? (set (map (juxt :name :email)
                                     (vals (:application/invitation-tokens application))))
                           [(:name (:member cmd))
                            (:email (:member cmd))])
        {:errors [{:type :user-not-member :user (:member cmd)}]})
      (add-comment-and-attachments cmd injections
                                   {:event/type :application.event/member-uninvited
                                    :application/member (:member cmd)})))

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
               (str (copy-attachment! new-application-id (Integer/parseInt value))))})))

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
            values (copy-field-values! (getx injections :copy-attachment!) application new-app-id)]
        (ok-with-data
         {:application-id new-app-id}
         [created-event
          {:event/type :application.event/draft-saved
           :application/id new-app-id
           :application/field-values values}
          {:event/type :application.event/copied-from
           :application/id new-app-id
           :application/copied-from (select-keys application [:application/id :application/external-id])}
          {:event/type :application.event/copied-to
           :application/id old-app-id
           :application/copied-to (select-keys created-event [:application/id :application/external-id])}])))))

(defmethod command-handler :application.command/assign-external-id
  [cmd _application _injections]
  (ok {:event/type :application.event/external-id-assigned
       :application/external-id (:external-id cmd)}))

(defn- add-common-event-fields-from-command [event cmd]
  (-> event
      (update :application/id (fn [app-id]
                                (or app-id (:application-id cmd))))
      (assoc :event/time (:time cmd)
             :event/actor (:actor cmd))))

(defn- finalize-events [result cmd]
  (update-present result :events (fn [events]
                                   (mapv #(add-common-event-fields-from-command % cmd) events))))

(defn- forbidden-error [application cmd]
  (let [permissions (if application
                      (permissions/user-permissions application (:actor cmd))
                      #{:application.command/create})]
    (when-not (contains? permissions (:type cmd))
      {:errors [{:type :forbidden}]})))

(defn handle-command [cmd application injections]
  (validate-command cmd) ; this is here mostly for tests, commands via the api are validated by compojure-api
  (let [result (-> cmd
                   (command-handler application injections)
                   (finalize-events cmd))]
    (or (when (:errors result) result) ;; prefer more specific errors
        (forbidden-error application cmd)
        result)))
