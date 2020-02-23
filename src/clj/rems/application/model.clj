(ns rems.application.model
  (:require [clojure.test :refer [deftest is testing]]
            [medley.core :refer [assoc-some find-first map-vals update-existing]]
            [rems.application.events :as events]
            [rems.application.master-workflow :as master-workflow]
            [rems.common.application-util :as application-util]
            [rems.common.form :refer [field-visible?]]
            [rems.common.util :refer [build-index getx getcat-in update-each update-in-each]]
            [rems.permissions :as permissions]
            [rems.util :refer [conj-vec]]))

;;;; Application

(def states
  #{:application.state/approved
    :application.state/closed
    :application.state/draft
    :application.state/rejected
    :application.state/returned
    :application.state/revoked
    :application.state/submitted})

(defmulti ^:private event-type-specific-application-view
  "See `application-view`"
  (fn [_application event] (:event/type event)))

(defmethod event-type-specific-application-view :application.event/created
  [application event]
  (-> application
      (assoc :application/id (:application/id event)
             :application/external-id (:application/external-id event)
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
             :application/form (select-keys (first (:application/forms event)) [:form/id]) ; TODO deprecate from API
             :application/forms (mapv #(select-keys % [:form/id]) (:application/forms event))
             :application/workflow {:workflow/id (:workflow/id event)
                                    :workflow/type (:workflow/type event)})))

(defmethod event-type-specific-application-view :application.event/draft-saved
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (assoc ::draft-answers (:application/field-values event))))

(defmethod event-type-specific-application-view :application.event/licenses-accepted
  [application event]
  (-> application
      (assoc-in [:application/accepted-licenses (:event/actor event)] (:application/accepted-licenses event))))

(defmethod event-type-specific-application-view :application.event/licenses-added
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (update :application/licenses
              (fn [licenses]
                (-> licenses
                    (into (:application/licenses event))
                    distinct
                    vec)))))

(defmethod event-type-specific-application-view :application.event/member-invited
  [application event]
  (-> application
      (update :application/invitation-tokens assoc (:invitation/token event) (:application/member event))))

(defmethod event-type-specific-application-view :application.event/member-uninvited
  [application event]
  (-> application
      (update :application/invitation-tokens (fn [invitations]
                                               (->> invitations
                                                    (remove (fn [[_token member]]
                                                              (= member (:application/member event))))
                                                    (into {}))))))

(defmethod event-type-specific-application-view :application.event/member-joined
  [application event]
  (-> application
      (update :application/members conj {:userid (:event/actor event)})
      (update :application/invitation-tokens dissoc (:invitation/token event))))

(defmethod event-type-specific-application-view :application.event/member-added
  [application event]
  (-> application
      (update :application/members conj (:application/member event))))

(defmethod event-type-specific-application-view :application.event/member-removed
  [application event]
  (-> application
      (update :application/members disj (:application/member event))
      (update :application/past-members conj (:application/member event))))

(defmethod event-type-specific-application-view :application.event/submitted
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

(defmethod event-type-specific-application-view :application.event/returned
  [application _event]
  (-> application
      (assoc ::draft-answers (::submitted-answers application)) ; guard against re-submit without saving a new draft
      (assoc :application/state :application.state/returned)
      (assoc :application/todo nil)))

(defn- update-todo-for-requests [application]
  (assoc application :application/todo
         (cond
           (not (empty? (::latest-review-request-by-user application)))
           :waiting-for-review
           (not (empty? (::latest-decision-request-by-user application)))
           :waiting-for-decision
           :else
           :no-pending-requests)))

(defmethod event-type-specific-application-view :application.event/review-requested
  [application event]
  (-> application
      (update ::latest-review-request-by-user merge (zipmap (:application/reviewers event)
                                                            (repeat (:application/request-id event))))
      (update-todo-for-requests)))

(defmethod event-type-specific-application-view :application.event/reviewed
  [application event]
  (-> application
      (update ::latest-review-request-by-user dissoc (:event/actor event))
      (update-todo-for-requests)))

(defmethod event-type-specific-application-view :application.event/decision-requested
  [application event]
  (-> application
      (update ::latest-decision-request-by-user merge (zipmap (:application/deciders event)
                                                              (repeat (:application/request-id event))))
      (update-todo-for-requests)))

(defmethod event-type-specific-application-view :application.event/decided
  [application event]
  (-> application
      (update ::latest-decision-request-by-user dissoc (:event/actor event))
      (update-todo-for-requests)))

(defmethod event-type-specific-application-view :application.event/remarked
  [application _event]
  application)

(defmethod event-type-specific-application-view :application.event/approved
  [application _event]
  (-> application
      (assoc :application/state :application.state/approved)
      (assoc :application/todo nil)))

(defmethod event-type-specific-application-view :application.event/rejected
  [application _event]
  (-> application
      (assoc :application/state :application.state/rejected)
      (assoc :application/todo nil)))

(defmethod event-type-specific-application-view :application.event/resources-changed
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (assoc :application/resources (vec (:application/resources event)))
      (assoc :application/licenses (map #(select-keys % [:license/id])
                                        (:application/licenses event)))))

(defmethod event-type-specific-application-view :application.event/closed
  [application _event]
  (-> application
      (assoc :application/state :application.state/closed)
      (assoc :application/todo nil)))

(defmethod event-type-specific-application-view :application.event/revoked
  [application _event]
  (-> application
      (assoc :application/state :application.state/revoked)
      (assoc :application/todo nil)))

(defmethod event-type-specific-application-view :application.event/copied-from
  [application event]
  (-> application
      (assoc :application/copied-from (:application/copied-from event))
      (assoc ::submitted-answers (::draft-answers application))))

(defmethod event-type-specific-application-view :application.event/copied-to
  [application event]
  (-> application
      (update :application/copied-to conj-vec (:application/copied-to event))))

(defmethod event-type-specific-application-view :application.event/external-id-assigned
  [application event]
  (assoc application :application/external-id (:application/external-id event)))

(deftest test-event-type-specific-application-view
  (testing "supports all event types"
    (is (= (set (keys events/event-schemas))
           (set (keys (methods event-type-specific-application-view)))))))

(defn- assert-same-application-id [application event]
  (assert (= (:application/id application)
             (:application/id event))
          (str "event for wrong application "
               "(not= " (:application/id application) " " (:application/id event) ")"))
  application)

(def default-workflow
  (permissions/compile-rules
   [{:permission :see-everything}
    {:permission :application.command/accept-invitation}
    {:permission :application.command/accept-licenses}
    {:permission :application.command/add-licenses}
    {:permission :application.command/add-member}
    {:permission :application.command/assign-external-id}
    {:permission :application.command/change-resources}
    {:permission :application.command/close}
    {:permission :application.command/copy-as-new}
    {:permission :application.command/create}
    {:permission :application.command/decide}
    {:permission :application.command/invite-member}
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
    {:role :handler :permission :application.command/reject}]))

(def decider-workflow
  (permissions/compile-rules
   [{:permission :see-everything}
    {:permission :application.command/accept-invitation}
    {:permission :application.command/accept-licenses}
    {:permission :application.command/add-licenses}
    {:permission :application.command/add-member}
    {:permission :application.command/assign-external-id}
    {:permission :application.command/change-resources}
    {:permission :application.command/close}
    {:permission :application.command/copy-as-new}
    {:permission :application.command/create}
    {:permission :application.command/invite-member}
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
    {:role :decider :permission :application.command/reject}]))

(defn- calculate-permissions [application event]
  (let [whitelist (case (get-in application [:application/workflow :workflow/type])
                    :workflow/default default-workflow
                    :workflow/decider decider-workflow
                    :workflow/master master-workflow/whitelist)]
    (-> application
        (master-workflow/calculate-permissions event)
        (permissions/whitelist whitelist))))

(defn application-view
  "Projection for the current state of a single application.
  Pure function; must use `enrich-with-injections` to enrich the model with
  data from other entities."
  [application event]
  (-> application
      (event-type-specific-application-view event)
      (calculate-permissions event)
      (assert-same-application-id event)
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
  (let [form-template (get-form-template (:form/id form))
        default-fields (map #(assoc % :field/value "")
                            (:form/fields form-template))
        fields (merge-lists-by :field/id
                               default-fields
                               (:form/fields form))]
    (assoc form
           :form/title (:form/title form-template)
           :form/fields fields)))

(defn- set-application-description [application]
  (let [fields (getcat-in application [:application/forms :form/fields])
        description (->> fields
                         (find-first #(= :description (:field/type %)))
                         :field/value)]
    (assoc application :application/description (str description))))

(defn- enrich-resources [resources get-catalogue-item]
  (->> resources
       (map (fn [resource]
              (let [item (get-catalogue-item (:catalogue-item/id resource))]
                {:catalogue-item/id (:catalogue-item/id resource)
                 :resource/ext-id (:resource/ext-id resource)
                 :resource/id (:resource-id item)
                 :catalogue-item/title (localization-for :title item)
                 :catalogue-item/infourl (localization-for :infourl item)
                 ;; TODO: remove unused keys
                 :catalogue-item/start (:start item)
                 :catalogue-item/end (:end item)
                 :catalogue-item/enabled (:enabled item)
                 :catalogue-item/expired (:expired item)
                 :catalogue-item/archived (:archived item)})))
       (sort-by :catalogue-item/id)
       vec))

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

             {}))))

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
      (permissions/give-role-to-users :reporter (get-users-with-role :reporter))))

(defn- enrich-form-answers [form current-answers previous-answers]
  (let [form-id (:form/id form)
        current-answers (get current-answers form-id)
        previous-answers (get previous-answers form-id)
        fields (for [field (:form/fields form)
                     :let [field-id (:field/id field)
                           current-value (get current-answers field-id)
                           previous-value (get previous-answers field-id)]]
                 (assoc-some field
                             :field/value current-value
                             :field/previous-value previous-value))]
    (assoc form :form/fields fields)))

(defn enrich-answers [application]
  (let [answer-versions (remove nil? [(::draft-answers application)
                                      (::submitted-answers application)
                                      (::previous-submitted-answers application)])
        current-answers (first answer-versions)
        previous-answers (second answer-versions)]
    (-> application
        (dissoc ::draft-answers ::submitted-answers ::previous-submitted-answers)
        (update-each :application/forms enrich-form-answers current-answers previous-answers))))

(defn enrich-deadline [application get-config]
  (let [days ((get-config) :application-deadline-days)]
    (if (and days
             (:application/first-submitted application))
      (assoc application :application/deadline
             (.plusDays (:application/first-submitted application)
                        days))
      application)))

(defn- enrich-form-fields-visible [form]
  (let [answers (build-index [:field/id] :field/value (:form/fields form))
        update-field-visible #(assoc % :field/visible (field-visible? % answers))]
    (update-each form :form/fields update-field-visible)))

(defn enrich-field-visible [application]
  (update-each application :application/forms enrich-form-fields-visible))

(defn- enrich-disable-commands [application get-config]
  (permissions/blacklist application
                         (permissions/compile-rules
                          (for [command (:disable-commands (get-config))]
                            {:permission command}))))

(defn- support-deprecated-form [application]
  (assoc-some application :application/form (first (:application/forms application))))

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
      (update-each :application/forms enrich-form get-form-template)
      enrich-answers ; uses enriched form fields
      enrich-field-visible ; uses enriched form fields
      set-application-description
      (update :application/resources enrich-resources get-catalogue-item)
      (update :application/licenses enrich-licenses get-license)
      (update :application/events (partial mapv #(enrich-event % get-user get-catalogue-item)))
      (assoc :application/applicant (get-user (get-in application [:application/applicant :userid])))
      (assoc :application/attachments (get-attachments-for-application (getx application :application/id)))
      (enrich-user-attributes get-user)
      (enrich-blacklist blacklisted?) ;; uses enriched users
      (enrich-workflow-handlers get-workflow)
      (enrich-deadline get-config)
      (enrich-super-users get-users-with-role)
      (enrich-disable-commands get-config)
      (support-deprecated-form)))

(defn build-application-view [events injections]
  (-> (reduce application-view nil events)
      (enrich-with-injections injections)))


;;;; Authorization

(defn hide-sensitive-events [events]
  (->> events
       (remove (comp #{:application.event/review-requested
                       :application.event/reviewed
                       :application.event/decided
                       :application.event/decision-requested}
                     :event/type))
       (remove #(and (= :application.event/remarked
                        (:event/type %))
                     (not (:application/public %))))))

(defn- hide-sensitive-information [application]
  (-> application
      (dissoc :application/blacklist)
      (update :application/events hide-sensitive-events)
      (update :application/workflow dissoc :workflow.dynamic/handlers)))

(defn- hide-invitation-tokens [application]
  (-> application
      ;; the keys of the invitation-tokens map are secret
      (dissoc :application/invitation-tokens)
      (assoc :application/invited-members (set (vals (:application/invitation-tokens application))))
      (update :application/events (partial mapv #(dissoc % :invitation/token)))))

(defn- may-see-private-answers? [roles]
  (some #{:applicant :member :decider :past-decider :handler :reporter}
        roles))

(defn- apply-field-privacy [field roles]
  (if (or (= :public (:field/privacy field :public))
          (may-see-private-answers? roles))
    (assoc field :field/private false)
    (-> field
        (assoc :field/private true)
        (update-existing :field/value (constantly ""))
        (update-existing :field/previous-value (constantly "")))))

(defn apply-privacy [application roles]
  (-> application
      (update-in-each [:application/form :form/fields] apply-field-privacy roles) ; TODO: remove support for deprecated form
      (update-in-each [:application/forms :form/fields] apply-field-privacy roles)))

(defn- hide-non-public-information [application]
  (-> application
      hide-invitation-tokens
      ;; these are not used by the UI, so no need to expose them (especially the user IDs)
      (dissoc ::latest-review-request-by-user ::latest-decision-request-by-user)
      (dissoc :application/past-members)))

(defn- personalize-todo [application user-id]
  (cond-> application
    (contains? (::latest-review-request-by-user application) user-id)
    (assoc :application/todo :waiting-for-your-review)

    (contains? (::latest-decision-request-by-user application) user-id)
    (assoc :application/todo :waiting-for-your-decision)))

(defn see-application? [application user-id]
  (not= #{:everyone-else} (permissions/user-roles application user-id)))

(defn apply-user-permissions [application user-id]
  (let [see-application? (see-application? application user-id)
        roles (permissions/user-roles application user-id)
        permissions (permissions/user-permissions application user-id)
        see-everything? (contains? permissions :see-everything)]
    (when see-application?
      (-> (if see-everything?
            application
            (hide-sensitive-information application))
          (personalize-todo user-id)
          (hide-non-public-information)
          (apply-privacy roles)
          (assoc :application/permissions permissions)
          (assoc :application/roles roles)
          (permissions/cleanup)))))
