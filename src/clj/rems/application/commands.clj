(ns rems.application.commands
  (:require [clojure.test :refer :all]
            [rems.util :refer [getx getx-in]]
            [schema.core :as s]
            [schema-refined.core :as r]
            [rems.application.model :as model]
            [rems.permissions :as permissions]
            [rems.util :refer [assert-ex try-catch-ex]])
  (:import [org.joda.time DateTime]
           [clojure.lang ExceptionInfo]
           [java.util UUID]))

;;; Schemas

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
         ;; {s/Int s/Str} is what we want, but that isn't nicely representable as JSON
         :field-values [{:field s/Int
                         :value s/Str}]))

(s/defschema AcceptLicensesCommand
  (assoc CommandBase
         :accepted-licenses [s/Int]))

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

;; TODO RequestComment/Comment could be renamed to RequestReview/Review to be in line with the UI
(s/defschema RequestCommentCommand
  (assoc CommandBase
         :commenters [UserId]
         :comment s/Str))

(s/defschema CommentCommand
  (assoc CommandBase
         :comment s/Str))

(s/defschema RemarkCommand
  (assoc CommandBase
         :comment s/Str
         :public s/Bool))

(s/defschema AddLicensesCommand
  (assoc CommandBase
         :comment s/Str
         :licenses [s/Int]))

(s/defschema AddMemberCommand
  (assoc CommandBase
         :member {:userid UserId}))

(s/defschema ChangeResourcesCommand
  (assoc CommandBase
         (s/optional-key :comment) s/Str
         :catalogue-item-ids [s/Int]))

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

(s/defschema CopyAsNewCommand
  CommandBase)

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
   :application.command/remark RemarkCommand
   :application.command/reject RejectCommand
   :application.command/request-comment RequestCommentCommand
   :application.command/request-decision RequestDecisionCommand
   :application.command/remove-member RemoveMemberCommand
   :application.command/return ReturnCommand
   :application.command/save-draft SaveDraftCommand
   :application.command/submit SubmitCommand
   :application.command/uninvite-member UninviteMemberCommand
   :application.command/copy-as-new CopyAsNewCommand
   #_:application.command/withdraw})

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

(defn- invalid-user-error [user-id injections]
  (cond
    (not (:valid-user? injections)) {:errors [{:type :missing-injection :injection :valid-user?}]}
    (not ((:valid-user? injections) user-id)) {:errors [{:type :t.form.validation/invalid-user :userid user-id}]}))

(defn- invalid-users-errors
  "Checks the given users for validity and merges the errors"
  [user-ids injections]
  (apply merge-with into (keep #(invalid-user-error % injections) user-ids)))

(defn- invalid-catalogue-item-error [catalogue-item-id injections]
  (cond
    (not (:get-catalogue-item injections)) {:errors [{:type :missing-injection :injection :get-catalogue-item}]}
    (not ((:get-catalogue-item injections) catalogue-item-id)) {:errors [{:type :invalid-catalogue-item :catalogue-item-id catalogue-item-id}]}))

(defn- invalid-catalogue-items
  "Checks the given catalogue items for validity and merges the errors"
  [catalogue-item-ids injections]
  (apply merge-with into (keep #(invalid-catalogue-item-error % injections) catalogue-item-ids)))

(defn- changes-original-workflow
  "Checks that the given catalogue items are compatible with the original application from where the workflow is from. Applicant can't do it."
  [application catalogue-item-ids actor injections]
  (let [catalogue-items (map (:get-catalogue-item injections) catalogue-item-ids)
        original-workflow-id (get-in application [:application/workflow :workflow/id])
        new-workflow-ids (mapv :wfid catalogue-items)
        handlers (get-in application [:application/workflow :workflow.dynamic/handlers])]
    (when (and (not (contains? handlers actor))
               (apply not= original-workflow-id new-workflow-ids))
      {:errors [{:type :changes-original-workflow :workflow/id original-workflow-id :ids new-workflow-ids}]})))

(defn- changes-original-form
  "Checks that the given catalogue items are compatible with the original application from where the form is from. Applicant can't do it."
  [application catalogue-item-ids actor injections]
  (let [catalogue-items (map (:get-catalogue-item injections) catalogue-item-ids)
        original-form-id (get-in application [:application/form :form/id])
        new-form-ids (mapv :formid catalogue-items)
        handlers (get-in application [:application/workflow :workflow.dynamic/handlers])]
    (when (and (not (contains? handlers actor))
               (apply not= original-form-id new-form-ids))
      {:errors [{:type :changes-original-form :form/id original-form-id :ids new-form-ids}]})))

(defn- unbundlable-catalogue-items
  "Checks that the given catalogue items are bundlable by the given actor."
  [application catalogue-item-ids actor injections]
  (let [catalogue-items (map (:get-catalogue-item injections) catalogue-item-ids)
        handlers (get-in application [:application/workflow :workflow.dynamic/handlers])]
    (when (and (not (contains? handlers actor))
               (not= 1
                     (count (set (map :formid catalogue-items)))
                     (count (set (map :wfid catalogue-items)))))
      {:errors [{:type :unbundlable-catalogue-items :catalogue-item-ids catalogue-item-ids}]})))

(defn- validation-error [application {:keys [validate-fields]}]
  (let [errors (validate-fields (getx-in application [:application/form :form/fields]))]
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

(defn- ok-with-data [data events]
  (assoc data :events events))

(defn- ok [& events]
  (ok-with-data nil events))

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
      (unbundlable-catalogue-items application (:catalogue-item-ids cmd) (:actor cmd) injections)
      (changes-original-form application (:catalogue-item-ids cmd) (:actor cmd) injections)
      (changes-original-workflow application (:catalogue-item-ids cmd) (:actor cmd) injections)
      (ok (merge {:event/type :application.event/resources-changed
                  :application/resources (->> (:catalogue-item-ids cmd)
                                              (mapv (:get-catalogue-item injections))
                                              (mapv (fn [catalogue-item]
                                                      {:catalogue-item/id (:id catalogue-item)
                                                       :resource/ext-id (:resid catalogue-item)})))
                  :application/licenses (->> (:catalogue-item-ids cmd)
                                             (mapcat (:get-catalogue-item-licenses injections))
                                             distinct
                                             (mapv (fn [license]
                                                     {:license/id (:id license)})))}
                 (when (:comment cmd)
                   {:application/comment (:comment cmd)})))))

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
  (or (already-member-error application (:actor cmd))
      (invitation-token-error application (:token cmd))
      (ok-with-data {:application-id (:application-id cmd)}
                    [{:event/type :application.event/member-joined
                      :application/id (:application-id cmd)
                      :invitation/token (:token cmd)}])))

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

(defmethod command-handler :application.command/copy-as-new
  [cmd application {:keys [create-application!]}]
  (let [result (create-application! (:actor cmd) (map :catalogue-item/id (:application/resources application)))
        _ (assert (:success result) {:result result})
        new-app-id (:application-id result)]
    (ok-with-data
     {:application-id new-app-id}
     ;; TODO: it would be better to refactor create-application! so that it won't persist the created event, but it'll be returned here explicitly
     ;; TODO: add copied-to event to the original application
     [{:event/type :application.event/draft-saved
       :application/id new-app-id
       :application/field-values (->> (:form/fields (:application/form application))
                                      (map (fn [field]
                                             [(:field/id field) (:field/value field)]))
                                      (into {}))}
      {:event/type :application.event/copied-from
       :application/id new-app-id
       :application/copied-from (select-keys application [:application/id :application/external-id])}])))

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
  (let [permissions (permissions/user-permissions application (:actor cmd))]
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
