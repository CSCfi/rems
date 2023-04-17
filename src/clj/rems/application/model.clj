(ns rems.application.model
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [com.rpl.specter :refer [ALL transform select]]
            [medley.core :refer [assoc-some distinct-by find-first map-vals update-existing update-existing-in]]
            [rems.application.events :as events]
            [rems.application.master-workflow :as master-workflow]
            [rems.common.application-util :as application-util]
            [rems.common.form :as form]
            [rems.common.util :refer [assoc-some-in conj-vec getx getx-in into-vec]]
            [rems.permissions :as permissions]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [rems.ext.duo :as duo]))

;;;; Application

(def states
  #{:application.state/approved
    :application.state/closed
    :application.state/draft
    :application.state/rejected
    :application.state/returned
    :application.state/revoked
    :application.state/submitted})
;; TODO deleted state?

(defmulti ^:private application-base-view
  "Updates the data in the application based on the given event.
  Contrast with calculate-permissions which updates permissions based
  on events. See also application-view."
  (fn [_application event] (:event/type event)))

(defmethod application-base-view :application.event/created
  [application event]
  (-> application
      (assoc :application/id (:application/id event)
             :application/external-id (:application/external-id event)
             :application/generated-external-id (:application/external-id event)
             :application/state :application.state/draft
             :application/todo nil
             :application/created (:event/time event)
             :application/modified (:event/time event)
             :application/applicant {:userid (:event/actor event)}
             :application/members #{}
             :application/past-members #{}
             :application/invitation-tokens {}
             :application/resources (map #(select-keys % [:catalogue-item/id :resource/ext-id])
                                         (:application/resources event))
             :application/licenses (map #(select-keys % [:license/id])
                                        (:application/licenses event))
             :application/accepted-licenses {}
             :application/events []
             :application/forms (:application/forms event)
             :application/workflow {:workflow/id (:workflow/id event)
                                    :workflow/type (:workflow/type event)})))

(defmethod application-base-view :application.event/draft-saved
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (assoc ::draft-answers (:application/field-values event))
      (assoc-some-in [:application/duo :duo/codes] (when (:enable-duo rems.config/env)
                                                     (:application/duo-codes event)))))

(defmethod application-base-view :application.event/licenses-accepted
  [application event]
  (-> application
      (assoc-in [:application/accepted-licenses (:event/actor event)] (:application/accepted-licenses event))))

(defmethod application-base-view :application.event/licenses-added
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (update :application/licenses
              (fn [licenses]
                (-> licenses
                    (into (:application/licenses event))
                    distinct
                    vec)))))

(defmethod application-base-view :application.event/member-invited
  [application event]
  (-> application
      (assoc-in [:application/invitation-tokens (:invitation/token event)]
                (select-keys event [:event/actor :application/member]))))

(defmethod application-base-view :application.event/member-uninvited
  [application event]
  (-> application
      (update :application/invitation-tokens (fn [invitations]
                                               (->> invitations
                                                    (remove (fn [[_token invitation]]
                                                              (= (:application/member invitation)
                                                                 (:application/member event))))
                                                    (into {}))))))

(defmethod application-base-view :application.event/member-joined
  [application event]
  (-> application
      (update :application/members conj {:userid (:event/actor event)})
      (update :application/invitation-tokens dissoc (:invitation/token event))))

(defmethod application-base-view :application.event/member-added
  [application event]
  (-> application
      (update :application/members conj (:application/member event))))

(defmethod application-base-view :application.event/member-removed
  [application event]
  (-> application
      (update :application/members disj (:application/member event))
      (update :application/past-members conj (:application/member event))))

(defmethod application-base-view :application.event/applicant-changed
  [application event]
  (-> application
      (update :application/members disj (:application/applicant event))
      (update :application/members conj (:application/applicant application))
      (assoc :application/applicant (:application/applicant event))))

(defmethod application-base-view :application.event/decider-invited
  [application event]
  (-> application
      (assoc-in [:application/invitation-tokens (:invitation/token event)]
                (select-keys event [:event/actor :application/decider]))))

(defmethod application-base-view :application.event/reviewer-invited
  [application event]
  (-> application
      (assoc-in [:application/invitation-tokens (:invitation/token event)]
                (select-keys event [:event/actor :application/reviewer]))))

(defn- update-todo-for-requests [application]
  (assoc application :application/todo
         (cond
           (seq (::latest-review-request-by-user application)) :waiting-for-review
           (seq (::latest-decision-request-by-user application)) :waiting-for-decision
           :else :no-pending-requests)))

(defmethod application-base-view :application.event/decider-joined
  [application event]
  (-> application
      (update :application/invitation-tokens dissoc (:invitation/token event))
      (assoc-in [::latest-decision-request-by-user (:event/actor event)] (:application/request-id event))
      (update-todo-for-requests)))

(defmethod application-base-view :application.event/reviewer-joined
  [application event]
  (-> application
      (update :application/invitation-tokens dissoc (:invitation/token event))
      (assoc-in [::latest-review-request-by-user (:event/actor event)] (:application/request-id event))
      (update-todo-for-requests)))

(defmethod application-base-view :application.event/submitted
  [application event]
  (-> application
      (assoc ::previous-submitted-answers (::submitted-answers application))
      (assoc ::submitted-answers (::draft-answers application))
      (update :application/first-submitted #(or % (:event/time event)))
      (dissoc ::draft-answers)
      (assoc :application/state :application.state/submitted)
      (assoc :application/todo (if (:application/first-submitted application)
                                 :resubmitted-application
                                 :new-application))))

(defmethod application-base-view :application.event/returned
  [application _event]
  (-> application
      (assoc ::draft-answers (::submitted-answers application)) ; guard against re-submit without saving a new draft
      (assoc :application/state :application.state/returned)
      (assoc :application/todo nil)))

(defmethod application-base-view :application.event/review-requested
  [application event]
  (-> application
      (update ::latest-review-request-by-user merge (zipmap (:application/reviewers event)
                                                            (repeat (:application/request-id event))))
      (update-todo-for-requests)))

(defmethod application-base-view :application.event/reviewed
  [application event]
  (-> application
      (update ::latest-review-request-by-user dissoc (:event/actor event))
      (update-todo-for-requests)))

(defmethod application-base-view :application.event/decision-requested
  [application event]
  (-> application
      (update ::latest-decision-request-by-user merge (zipmap (:application/deciders event)
                                                              (repeat (:application/request-id event))))
      (update-todo-for-requests)))

(defmethod application-base-view :application.event/decided
  [application event]
  (-> application
      (update ::latest-decision-request-by-user dissoc (:event/actor event))
      (update-todo-for-requests)))

(defn- set-redacted-attachments [attachments redacted-ids]
  (for [attachment attachments
        :let [id (:attachment/id attachment)]]
    (cond-> attachment
      (contains? redacted-ids id) (assoc :attachment/redacted true))))

(defmethod application-base-view :application.event/attachments-redacted
  [application event]
  (-> application
      (update :application/attachments set-redacted-attachments (->> event
                                                                     :event/redacted-attachments
                                                                     (into #{} (map :attachment/id))))))

(defmethod application-base-view :application.event/remarked
  [application _event]
  application)

(defmethod application-base-view :application.event/approved
  [application event]
  (-> application
      (assoc :application/state :application.state/approved)
      (merge (select-keys event [:entitlement/end]))
      (assoc :application/todo nil)))

(defmethod application-base-view :application.event/rejected
  [application _event]
  (-> application
      (assoc :application/state :application.state/rejected)
      (assoc :application/todo nil)))

(defmethod application-base-view :application.event/resources-changed
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (assoc :application/forms (vec (:application/forms event)))
      (assoc :application/resources (vec (:application/resources event)))
      (assoc :application/licenses (map #(select-keys % [:license/id])
                                        (:application/licenses event)))))

(defmethod application-base-view :application.event/closed
  [application _event]
  (-> application
      (assoc :application/state :application.state/closed)
      (assoc :application/todo nil)))

(defmethod application-base-view :application.event/revoked
  [application _event]
  (-> application
      (assoc :application/state :application.state/revoked)
      (assoc :application/todo nil)))

(defmethod application-base-view :application.event/copied-from
  [application event]
  (-> application
      (assoc :application/copied-from (:application/copied-from event))
      (assoc ::submitted-answers (::draft-answers application))))

(defmethod application-base-view :application.event/copied-to
  [application event]
  (-> application
      (update :application/copied-to conj-vec (:application/copied-to event))))

(defmethod application-base-view :application.event/external-id-assigned
  [application event]
  (assoc application
         :application/external-id (:application/external-id event)
         :application/assigned-external-id (:application/external-id event)))

(defmethod application-base-view :application.event/deleted
  [application _event]
  application)

(defmethod application-base-view :application.event/expiration-notifications-sent
  [application _event]
  application)

(deftest test-event-type-specific-application-view
  (testing "supports all event types"
    (is (= (set (keys events/event-schemas))
           (set (keys (methods application-base-view)))))))

(defn- assert-same-application-id [application event]
  (assert (= (:application/id application)
             (:application/id event))
          (str "event for wrong application "
               "(not= " (:application/id application) " " (:application/id event) ")"))
  application)

;; Workflows are defined as whitelists that select a subset of the commands that master-workflow has.
;; See also:
;;  - master-workflow/application-permissions-view - computes the possible commands for master-workflow
;;  - application-permissions-for-workflow-view - applies the whitelist

(def default-workflow
  (permissions/compile-rules
   [{:permission :see-everything}
    {:permission :application.command/accept-invitation}
    {:permission :application.command/accept-licenses}
    {:permission :application.command/add-licenses}
    {:permission :application.command/add-member}
    {:permission :application.command/assign-external-id}
    {:permission :application.command/change-applicant}
    {:permission :application.command/change-resources}
    {:permission :application.command/close}
    {:permission :application.command/copy-as-new}
    {:permission :application.command/create}
    {:permission :application.command/decide}
    {:permission :application.command/delete}
    {:permission :application.command/invite-decider}
    {:permission :application.command/invite-member}
    {:permission :application.command/invite-reviewer}
    {:permission :application.command/redact-attachments}
    {:permission :application.command/remark}
    {:permission :application.command/remove-member}
    {:permission :application.command/request-decision}
    {:permission :application.command/request-review}
    {:permission :application.command/return}
    {:permission :application.command/review}
    {:permission :application.command/revoke}
    {:permission :application.command/save-draft}
    {:permission :application.command/submit}
    {:permission :application.command/uninvite-member}
    {:role :handler :permission :application.command/approve}
    {:role :handler :permission :application.command/reject}
    {:role :expirer :permission :application.command/delete}
    {:role :expirer :permission :application.command/send-expiration-notifications}]))

(def decider-workflow
  (permissions/compile-rules
   [{:permission :see-everything}
    {:permission :application.command/accept-invitation}
    {:permission :application.command/accept-licenses}
    {:permission :application.command/add-licenses}
    {:permission :application.command/add-member}
    {:permission :application.command/assign-external-id}
    {:permission :application.command/change-applicant}
    {:permission :application.command/change-resources}
    {:permission :application.command/close}
    {:permission :application.command/copy-as-new}
    {:permission :application.command/create}
    {:permission :application.command/delete}
    {:permission :application.command/invite-member}
    {:permission :application.command/invite-reviewer}
    {:permission :application.command/redact-attachments}
    {:permission :application.command/remark}
    {:permission :application.command/remove-member}
    {:permission :application.command/request-decision}
    {:permission :application.command/request-review}
    {:permission :application.command/return}
    {:permission :application.command/review}
    {:permission :application.command/revoke}
    {:permission :application.command/save-draft}
    {:permission :application.command/submit}
    {:permission :application.command/uninvite-member}
    {:role :decider :permission :application.command/approve}
    {:role :decider :permission :application.command/reject}
    {:role :expirer :permission :application.command/delete}
    {:role :expirer :permission :application.command/send-expiration-notifications}]))

(defn- application-permissions-for-workflow-view [application event]
  (let [whitelist (case (get-in application [:application/workflow :workflow/type])
                    :workflow/default default-workflow
                    :workflow/decider decider-workflow
                    :workflow/master master-workflow/whitelist)]
    (-> application
        (master-workflow/application-permissions-view event)
        (permissions/whitelist whitelist))))

(defn- application-attachments [application event]
  (-> application
      (update :application/attachments into-vec (for [att (:event/attachments event)]
                                                  {:attachment/id (:attachment/id att)
                                                   :attachment/event (select-keys event [:event/id])}))))

(defn application-view
  "Projection for the current state of a single application.
  Pure function; must use `enrich-with-injections` to enrich the model with
  data from other entities."
  [application event]
  (-> application
      (application-base-view event)
      (application-permissions-for-workflow-view event)
      (assert-same-application-id event)
      (application-attachments event)
      (assoc :application/last-activity (:event/time event))
      (update :application/events conj event)))

;;;; Injections

(defn- merge-lists-by
  "Returns a list of merged elements from list1 and list2
   where f returned the same value for both elements."
  [f list1 list2]
  (let [groups (group-by f (concat list1 list2))
        merged-groups (map-vals #(apply merge %) groups)
        merged-in-order (map (fn [item1]
                               (get merged-groups (f item1)))
                             list1)
        list1-keys (set (map f list1))
        orphans-in-order (filter (fn [item2]
                                   (not (contains? list1-keys (f item2))))
                                 list2)]
    (vec (concat merged-in-order orphans-in-order))))

(deftest test-merge-lists-by
  (testing "merges objects with the same key"
    (is (= [{:id 1 :foo "foo1" :bar "bar1"}
            {:id 2 :foo "foo2" :bar "bar2"}]
           (merge-lists-by :id
                           [{:id 1 :foo "foo1"}
                            {:id 2 :foo "foo2"}]
                           [{:id 1 :bar "bar1"}
                            {:id 2 :bar "bar2"}]))))
  (testing "last list overwrites values"
    (is (= [{:id 1 :foo "B"}]
           (merge-lists-by :id
                           [{:id 1 :foo "A"}]
                           [{:id 1 :foo "B"}]))))
  (testing "first list determines the order"
    (is (= [{:id 1} {:id 2}]
           (merge-lists-by :id
                           [{:id 1} {:id 2}]
                           [{:id 2} {:id 1}])))
    (is (= [{:id 2} {:id 1}]
           (merge-lists-by :id
                           [{:id 2} {:id 1}]
                           [{:id 1} {:id 2}]))))
  ;; TODO: or should the unmatched items be discarded? the primary use case is that some fields are removed from a form (unless forms are immutable)
  (testing "unmatching items are added to the end in order"
    (is (= [{:id 1} {:id 2} {:id 3} {:id 4}]
           (merge-lists-by :id
                           [{:id 1} {:id 2}]
                           [{:id 3} {:id 4}])))
    (is (= [{:id 4} {:id 3} {:id 2} {:id 1}]
           (merge-lists-by :id
                           [{:id 4} {:id 3}]
                           [{:id 2} {:id 1}])))))

(defn- localization-for [key item]
  (into {} (for [lang (keys (:localizations item))]
             (when-let [text (get-in item [:localizations lang key])]
               [lang text]))))

(deftest test-localization-for
  (is (= {:en "en title" :fi "fi title"}
         (localization-for :title {:localizations {:en {:title "en title"}
                                                   :fi {:title "fi title"}}})))
  (is (= {:en "en title"}
         (localization-for :title {:localizations {:en {:title "en title"}
                                                   :fi {}}})))
  (is (= {}
         (localization-for :title {:localizations {:en {}
                                                   :fi {}}}))))

(defn- enrich-form [form get-form-template]
  (let [form-template (form/add-default-values (get-form-template (:form/id form)))
        fields (merge-lists-by :field/id
                               (:form/fields form-template)
                               (:form/fields form))]
    (assoc form
           :form/title (:form/internal-name form-template)
           :form/internal-name (:form/internal-name form-template)
           :form/external-title (:form/external-title form-template)
           :form/fields fields)))

(defn enrich-forms [forms get-form-template]
  (mapv #(enrich-form % get-form-template) forms))

(defn- set-application-description [application]
  (let [fields (select [:application/forms ALL :form/fields ALL] application)
        description (->> fields
                         (find-first #(= :description (:field/type %)))
                         :field/value)]
    (assoc application :application/description (str description))))

(def ^:private coerce-DuoCodesDb
  (coerce/coercer! [schema-base/DuoCode] coerce/string-coercion-matcher))

(defn- enrich-resources [resources get-catalogue-item]
  (->> resources
       (map (fn [resource]
              (let [item (get-catalogue-item (:catalogue-item/id resource))
                    duo-codes (-> (:resourcedata item)
                                  json/parse-string
                                  (get-in [:resource/duo :duo/codes])
                                  coerce-DuoCodesDb)]
                (-> {:catalogue-item/id (:catalogue-item/id resource)
                     :resource/ext-id (:resource/ext-id resource)
                     :resource/id (:resource-id item)
                     :catalogue-item/title (localization-for :title item)
                     :catalogue-item/infourl (localization-for :infourl item)
                 ;; TODO: remove unused keys
                     :catalogue-item/start (:start item)
                     :catalogue-item/end (:end item)
                     :catalogue-item/enabled (:enabled item)
                     :catalogue-item/expired (:expired item)
                     :catalogue-item/archived (:archived item)}
                    (assoc-some-in [:resource/duo :duo/codes] (seq (duo/enrich-duo-codes duo-codes)))))))
       (sort-by :catalogue-item/id)
       vec))

(defn- validate-duo-match [dataset-code query-code resource]
  (let [validity (duo/check-duo-code dataset-code query-code)]
    {:validity validity
     :errors (case validity
               :duo/not-compatible (case (:id dataset-code)
                                     "DUO:0000007" [{:type :t.duo.validation/mondo-not-valid
                                                     :duo/restrictions (duo/get-restrictions dataset-code :mondo)
                                                     :catalogue-item/title (:catalogue-item/title resource)}]
                                     nil)
               :duo/needs-manual-validation [{:type :t.duo.validation/needs-validation
                                              :catalogue-item/title (:catalogue-item/title resource)
                                              :duo/restrictions (:restrictions dataset-code)}]
               nil)}))

(defn- enrich-application-duo-matches [application]
  (if-not (:enable-duo rems.config/env)
    application
    (let [duos (->> application :application/duo :duo/codes)
          matches (for [resource (:application/resources application)
                        res-duo (-> resource :resource/duo :duo/codes)
                        :let [app-duo (find-first #(= (:id res-duo) (:id %)) duos)]]
                    {:duo/id (:id res-duo)
                     :duo/shorthand (:shorthand res-duo)
                     :duo/label (:label res-duo)
                     :resource/id (:resource/id resource)
                     :duo/validation (validate-duo-match res-duo app-duo resource)})]
      (-> application
          (assoc-in [:application/duo :duo/matches] matches)))))

(defn- enrich-workflow-licenses [application get-workflow]
  (let [wf (get-workflow (get-in application [:application/workflow :workflow/id]))]
    (if-some [workflow-licenses (seq (get-in wf [:workflow :licenses]))]
      (-> application
          (update :application/licenses (fn [app-licenses]
                                          (->> workflow-licenses
                                               (into app-licenses)
                                               (distinct-by :license/id)))))
      application)))

(defn- enrich-licenses [app-licenses get-license]
  (let [rich-licenses (->> app-licenses
                           (map :license/id)
                           (map get-license)
                           (map (fn [license]
                                  (let [license-type (keyword (:licensetype license))]
                                    (merge {:license/id (:id license)
                                            :license/type license-type
                                            :license/title (localization-for :title license)
                                            ;; TODO: remove unused keys
                                            :license/enabled (:enabled license)
                                            :license/archived (:archived license)}
                                           (case license-type
                                             :text {:license/text (localization-for :textcontent license)}
                                             :link {:license/link (localization-for :textcontent license)}
                                             :attachment {:license/attachment-id (localization-for :attachment-id license)
                                                          ;; TODO: remove filename as unused?
                                                          :license/attachment-filename (localization-for :textcontent license)})))))
                           (sort-by :license/id))]
    (merge-lists-by :license/id rich-licenses app-licenses)))

(defn enrich-event [event get-user get-catalogue-item]
  (let [event-type (:event/type event)]
    (merge event
           {:event/actor-attributes (get-user (:event/actor event))}
           (case event-type
             :application.event/resources-changed
             {:application/resources (enrich-resources (:application/resources event) get-catalogue-item)}

             :application.event/decision-requested
             {:application/deciders (mapv get-user (:application/deciders event))}

             :application.event/review-requested
             {:application/reviewers (mapv get-user (:application/reviewers event))}

             (:application.event/member-added
              :application.event/member-removed)
             {:application/member (get-user (:userid (:application/member event)))}

             :application.event/applicant-changed
             {:application/applicant (get-user (:userid (:application/applicant event)))}

             {}))))

(defn classify-attachments [application]
  (let [from-events (for [event (:application/events application)
                          attachment (:event/attachments event)]
                      {(:attachment/id attachment) #{:event/attachments}})
        from-fields (for [form (getx application :application/forms)
                          field (getx form :form/fields)
                          :when (= :attachment (:field/type field))
                          k [:field/value :field/previous-value]
                          id (form/parse-attachment-ids (get field k))]
                      {id #{k}})]
    (apply merge-with set/union (concat from-events from-fields))))

(deftest test-classify-attachments
  (let [application {:application/events [{:event/type :application.event/foo
                                           :event/attachments [{:attachment/id 1}
                                                               {:attachment/id 3}]}
                                          {:event/type :application.event/bar}]
                     :application/forms [{:form/fields [{:field/type :attachment
                                                         :field/value "5" :field/previous-value "5,1,15"}]}
                                         {:form/fields [{:field/type :text
                                                         :field/value "2" :field/previous-value "2"}
                                                        {:field/type :attachment
                                                         :field/value "9,11,13"}]}]}]
    (is (= {1 #{:event/attachments :field/previous-value}
            3 #{:event/attachments}
            5 #{:field/value :field/previous-value}
            9 #{:field/value}
            11 #{:field/value}
            13 #{:field/value}
            15 #{:field/previous-value}}
           (classify-attachments application)))))

(defn- get-blacklist [application blacklisted?]
  (let [all-members (application-util/applicant-and-members application)
        all-resources (distinct (map :resource/ext-id (:application/resources application)))]
    (vec
     (for [member all-members
           resource all-resources
           :when (blacklisted? (:userid member) resource)]
       {:blacklist/user member :blacklist/resource {:resource/ext-id resource}}))))

(defn- enrich-blacklist [application blacklisted?]
  (assoc application :application/blacklist (get-blacklist application blacklisted?)))

(defn- enrich-user-attributes [application get-user]
  (letfn [(enrich-members [members]
            (->> members
                 (map (fn [member]
                        (merge member
                               (get-user (:userid member)))))
                 set))]
    (update application
            :application/members
            enrich-members)))

(defn enrich-workflow-handlers [application get-workflow]
  (let [workflow (get-workflow (get-in application [:application/workflow :workflow/id]))
        handlers (get-in workflow [:workflow :handlers])
        active-users (set (map :event/actor (:application/events application)))
        handlers (map (fn [handler]
                        (if (contains? active-users (:userid handler))
                          (assoc handler :handler/active? true)
                          handler))
                      handlers)]
    (-> application
        (assoc-in [:application/workflow :workflow.dynamic/handlers] handlers)
        (permissions/give-role-to-users :handler (mapv :userid handlers)))))

(defn- enrich-super-users [application get-users-with-role]
  (-> application
      (permissions/give-role-to-users :reporter (get-users-with-role :reporter))
      (permissions/give-role-to-users :expirer (get-users-with-role :expirer))))

(defn add-answers [application current-answers previous-answers]
  (transform [:application/forms ALL] #(form/enrich-form-answers % current-answers previous-answers) application))

(defn enrich-answers [application]
  (let [answer-versions (remove nil? [(::draft-answers application)
                                      (::submitted-answers application)
                                      (::previous-submitted-answers application)])
        current-answers (first answer-versions)
        previous-answers (second answer-versions)]
    (-> application
        (dissoc ::draft-answers ::submitted-answers ::previous-submitted-answers)
        (add-answers current-answers previous-answers))))

(defn enrich-deadline [application get-config]
  (let [days ((get-config) :application-deadline-days)]
    (if (and days
             (:application/first-submitted application))
      (assoc application :application/deadline
             (.plusDays (:application/first-submitted application)
                        days))
      application)))

(defn enrich-field-visible [application]
  (transform [:application/forms ALL]
             form/enrich-form-field-visible
             application))

(defn- enrich-disable-commands [application get-config]
  (permissions/blacklist application
                         (permissions/compile-rules
                          (for [command (:disable-commands (get-config))]
                            {:permission command}))))

(defn- get-attachment-redact-roles [attachment application]
  (when (:attachment/event attachment)
    (let [attachment-user-roles (->> (getx-in attachment [:attachment/user :userid])
                                     (permissions/user-roles application))]
      (cond
        (some #{:handler} attachment-user-roles)
        #{}

        (and (= :workflow/decider (get-in application [:application/workflow :workflow/type]))
             (some #{:decider :past-decider} attachment-user-roles))
        #{}

        :else
        #{:handler}))))

(defn- enrich-attachments [application get-user]
  (->> application
       (transform [:application/attachments ALL] #(update % :attachment/user get-user))
       (transform [:application/attachments ALL] #(let [roles (get-attachment-redact-roles % application)]
                                                    (assoc-some % :attachment/redact-roles roles)))))

(defn enrich-with-injections
  [application {:keys [blacklisted?
                       get-form-template
                       get-catalogue-item
                       get-license
                       get-user
                       get-users-with-role
                       get-workflow
                       get-attachments-for-application
                       get-config]
                :as _injections}]
  (-> application
      (update :application/forms enrich-forms get-form-template)
      enrich-answers ; uses enriched form
      enrich-field-visible ; uses enriched answers
      set-application-description ; uses enriched answers
      (update :application/resources enrich-resources get-catalogue-item)
      enrich-application-duo-matches ; uses enriched resources
      (update-existing-in [:application/duo :duo/codes] duo/enrich-duo-codes)
      (enrich-workflow-licenses get-workflow)
      (update :application/licenses enrich-licenses get-license)
      (update :application/events (partial mapv #(enrich-event % get-user get-catalogue-item)))
      (assoc :application/applicant (get-user (get-in application [:application/applicant :userid])))
      (update :application/attachments #(merge-lists-by :attachment/id % (get-attachments-for-application (getx application :application/id))))
      (enrich-user-attributes get-user)
      (enrich-blacklist blacklisted?) ; uses enriched users
      (enrich-workflow-handlers get-workflow)
      (enrich-deadline get-config)
      (enrich-super-users get-users-with-role)
      (enrich-disable-commands get-config)
      (enrich-attachments get-user)))

(defn build-application-view [events injections]
  (-> (reduce application-view nil events)
      (enrich-with-injections injections)))

;;;; Authorization

(def ^:private sensitive-events #{:application.event/review-requested
                                  :application.event/reviewed
                                  :application.event/reviewer-invited
                                  :application.event/reviewer-joined
                                  :application.event/decided
                                  :application.event/decider-invited
                                  :application.event/decider-joined
                                  :application.event/decision-requested})
(deftest test-sensitive-events
  (let [public-events #{:application.event/attachments-redacted
                        :application.event/applicant-changed
                        :application.event/approved
                        :application.event/closed
                        :application.event/copied-from
                        :application.event/copied-to
                        :application.event/created
                        :application.event/deleted
                        :application.event/draft-saved
                        :application.event/external-id-assigned
                        :application.event/expiration-notifications-sent
                        :application.event/licenses-accepted
                        :application.event/licenses-added
                        :application.event/member-added
                        :application.event/member-invited
                        :application.event/member-joined
                        :application.event/member-removed
                        :application.event/member-uninvited
                        :application.event/rejected
                        :application.event/remarked
                        :application.event/resources-changed
                        :application.event/returned
                        :application.event/revoked
                        :application.event/submitted}]
    (is (= #{}
           (set/intersection sensitive-events public-events)))
    (is (= #{}
           (set/difference (set events/event-types)
                           (set/union public-events sensitive-events)))
        "seems like a new event has been added; is public or sensitive?")))

(defn- hide-sensitive-events [events]
  (->> events
       (remove (comp sensitive-events :event/type))
       (remove (comp false? :application/public)))) ; :application/public might not be set

(defn- censor-user [user]
  (select-keys user [:userid :name :email :organizations :notification-email]))

(defn- censor-users-in-event [event]
  (-> event
      (update-existing :event/actor-attributes censor-user)
      (update-existing :application/member censor-user)
      ;; deciders and reviewers occur only in events removed by
      ;; hide-sensitive-events, but keeping the code here for
      ;; completeness
      (update-existing :application/deciders (partial mapv censor-user))
      (update-existing :application/reviewers (partial mapv censor-user))))

(defn- hide-extra-user-attributes [application]
  ;; To catch all the places that might have user attributes, grep this file for uses of the get-user injection.
  (-> application
      (update :application/applicant censor-user)
      (update :application/members (comp set (partial mapv censor-user)))
      ;; hide-sensitive-information has already dissoced the
      ;; blacklist, so this is unnecessary. Keeping it here for
      ;; completeness anyway.
      (update :application/blacklist (partial mapv #(update % :blacklist/user censor-user)))
      (update :application/events (partial mapv censor-users-in-event))
      (update :application/attachments (partial mapv #(update % :attachment/user censor-user)))))

(defn- hide-sensitive-information [application]
  (-> application
      (dissoc :application/blacklist)
      (update :application/events hide-sensitive-events)
      (update :application/workflow dissoc :workflow.dynamic/handlers)
      hide-extra-user-attributes))

(defn- hide-invitation-tokens [application]
  (-> application
      ;; the keys of the invitation-tokens map are secret
      (dissoc :application/invitation-tokens)
      (assoc :application/invited-members (->> application
                                               :application/invitation-tokens
                                               vals
                                               (keep :application/member)
                                               set))
      (update :application/events (partial mapv #(dissoc % :invitation/token)))))

(defn- may-see-private-answers? [roles]
  (some #{:applicant :member :decider :past-decider :handler :reporter}
        roles))

(defn- apply-field-privacy [field roles]
  (if (or (= :public (:field/privacy field :public))
          (may-see-private-answers? roles))
    (assoc field :field/private false)
    (let [private (-> field
                      (assoc :field/private true)
                      (form/add-default-field-value)) ; zeros :field/value
          value (:field/value private)]
      (update-existing private :field/previous-value (constantly value)))))

(defn apply-privacy-by-roles [application roles]
  (->> application
       (transform [:application/forms ALL :form/fields ALL] #(apply-field-privacy % roles))))

(defn- may-see-redacted-filename [roles]
  (some #{:handler :reporter}
        roles))

(defn- apply-attachment-privacy [attachment roles userid]
  (let [see-filename (or (not (:attachment/redacted attachment))
                         (= userid (get-in attachment [:attachment/user :userid]))
                         (may-see-redacted-filename roles))]
    (cond-> attachment
      (not see-filename) (assoc :attachment/filename :filename/redacted))))

(defn apply-privacy-by-user [application roles userid]
  (->> application
       (transform [:application/attachments ALL] #(apply-attachment-privacy % roles userid))))

(defn apply-attachment-permissions [attachment roles userid]
  (-> attachment
      (assoc-some :attachment/can-redact (application-util/can-redact-attachment attachment roles userid))
      (dissoc :attachment/redact-roles)))

(defn hide-non-public-information [application]
  (-> application
      hide-invitation-tokens
      ;; these are not used by the UI, so no need to expose them (especially the user IDs)
      (dissoc ::latest-review-request-by-user ::latest-decision-request-by-user)
      (dissoc :application/past-members)))

(defn- personalize-todo [application userid]
  (cond-> application
    (contains? (::latest-review-request-by-user application) userid)
    (assoc :application/todo :waiting-for-your-review)

    (contains? (::latest-decision-request-by-user application) userid)
    (assoc :application/todo :waiting-for-your-decision)))

(defn- visible-attachment-ids [application]
  (->> application
       classify-attachments
       keys
       set))

(deftest test-visible-attachment-ids
  (let [application {:application/events [{:event/type :application.event/foo
                                           :event/attachments [{:attachment/id 1}
                                                               {:attachment/id 3}]}
                                          {:event/type :application.event/bar}]
                     :application/forms [{:form/fields [{:field/type :attachment
                                                         :field/value "5" :field/previous-value "7,15"}]}
                                         {:form/fields [{:field/type :text
                                                         :field/value "2" :field/previous-vaule "2"}
                                                        {:field/type :attachment
                                                         :field/value "9,11,13"}]}]}]
    (is (= #{1 3 5 7 9 11 13 15} (visible-attachment-ids application)))))

(defn- hide-attachments [application]
  (let [visible-ids (visible-attachment-ids application)
        visible? (comp visible-ids :attachment/id)]
    (update application :application/attachments #(filterv visible? %))))

(defn user-is-applicant-or-member [roles]
  (or (contains? roles :applicant)
      (contains? roles :member)))

(defn see-application? [application userid]
  (let [roles (permissions/user-roles application userid)]
    (if (= :application.state/draft (:application/state application))
      (user-is-applicant-or-member roles)
      (not= #{:everyone-else} roles))))

(defn apply-user-permissions [application userid]
  (let [roles (permissions/user-roles application userid)
        permissions (permissions/user-permissions application userid)
        see-application? (see-application? application userid)
        see-everything? (contains? permissions :see-everything)]
    (when see-application?
      (-> (if see-everything?
            application
            (hide-sensitive-information application))
          (personalize-todo userid)
          (hide-non-public-information)
          (apply-privacy-by-roles roles)
          (apply-privacy-by-user roles userid)
          (hide-attachments)
          (update :application/attachments (partial map #(apply-attachment-permissions % roles userid)))
          (assoc :application/permissions permissions)
          (assoc :application/roles roles)
          (permissions/cleanup)))))
