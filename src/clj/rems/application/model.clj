(ns rems.application.model
  (:require [clojure.test :refer [deftest is testing]]
            [medley.core :refer [map-vals]]
            [rems.application.events :as events]
            [rems.permissions :as permissions]
            [rems.util :refer [getx conj-vec]]))

;;;; Roles & Permissions

(defn see-application? [application user-id]
  (not= #{:everyone-else} (permissions/user-roles application user-id)))

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
              :application.command/decide}
   :past-decider #{:see-everything
                   :application.command/remark}})

(def ^:private returned-permissions
  {:applicant submittable-application-commands
   :handler (conj handler-returned-commands :see-everything)})

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
              :application.command/revoke}})

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
             :application/form {:form/id (:form/id event)}
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
           (not (empty? (::latest-comment-request-by-user application)))
           :waiting-for-review
           (not (empty? (::latest-decision-request-by-user application)))
           :waiting-for-decision
           :else
           :no-pending-requests)))

(defmethod event-type-specific-application-view :application.event/comment-requested
  [application event]
  (-> application
      (update ::latest-comment-request-by-user merge (zipmap (:application/commenters event)
                                                             (repeat (:application/request-id event))))
      (update-todo-for-requests)))

(defmethod event-type-specific-application-view :application.event/commented
  [application event]
  (-> application
      (update ::latest-comment-request-by-user dissoc (:event/actor event))
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
  (let [fields (get-in application [:application/form :form/fields])
        description (->> fields
                         (filter #(= :description (:field/type %)))
                         first
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

             :application.event/comment-requested
             {:application/commenters (mapv get-user (:application/commenters event))}

             (:application.event/member-added
              :application.event/member-removed)
             {:application/member (get-user (:userid (:application/member event)))}

             {}))))

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
  (if (= :workflow/dynamic (get-in application [:application/workflow :workflow/type]))
    (let [workflow (get-workflow (get-in application [:application/workflow :workflow/id]))
          handlers (get-in workflow [:workflow :handlers])]
      (-> application
          (assoc-in [:application/workflow :workflow.dynamic/handlers] handlers)
          (permissions/give-role-to-users :handler (mapv :userid handlers))))
    application))

(defn- enrich-super-users [application get-users-with-role]
  (-> application
      (permissions/give-role-to-users :reporter (get-users-with-role :reporter))))

(defn enrich-with-injections [application {:keys [get-form-template get-catalogue-item get-license
                                                  get-user get-users-with-role get-workflow
                                                  get-attachments-for-application]}]
  (let [answer-versions (remove nil? [(::draft-answers application)
                                      (::submitted-answers application)
                                      (::previous-submitted-answers application)])
        current-answers (first answer-versions)
        previous-answers (second answer-versions)]
    (-> application
        (dissoc ::draft-answers ::submitted-answers ::previous-submitted-answers)
        (assoc-in [:application/form :form/fields] (merge-lists-by :field/id
                                                                   (map (fn [[field-id value]]
                                                                          {:field/id field-id
                                                                           :field/previous-value value})
                                                                        previous-answers)
                                                                   (map (fn [[field-id value]]
                                                                          {:field/id field-id
                                                                           :field/value value})
                                                                        current-answers)))
        (update :application/form enrich-form get-form-template)
        set-application-description
        (update :application/resources enrich-resources get-catalogue-item)
        (update :application/licenses enrich-licenses get-license)
        (update :application/events (partial mapv #(enrich-event % get-user get-catalogue-item)))
        (assoc :application/applicant (get-user (get-in application [:application/applicant :userid])))
        (assoc :application/attachments (get-attachments-for-application (getx application :application/id)))
        (enrich-user-attributes get-user)
        (enrich-workflow-handlers get-workflow)
        (enrich-super-users get-users-with-role))))

(defn build-application-view [events injections]
  (-> (reduce application-view nil events)
      (enrich-with-injections injections)))


;;;; Authorization

(defn hide-sensitive-events [events]
  (->> events
       (remove (comp #{:application.event/comment-requested
                       :application.event/commented
                       :application.event/decided
                       :application.event/decision-requested}
                     :event/type))
       (remove #(and (= :application.event/remarked
                        (:event/type %))
                     (not (:application/public %))))))

(defn- hide-sensitive-information [application]
  (-> application
      (update :application/events hide-sensitive-events)
      (update :application/workflow dissoc :workflow.dynamic/handlers)))

(defn- hide-invitation-tokens [application]
  (-> application
      ;; the keys of the invitation-tokens map are secret
      (dissoc :application/invitation-tokens)
      (assoc :application/invited-members (set (vals (:application/invitation-tokens application))))
      (update :application/events (partial mapv #(dissoc % :invitation/token)))))

(defn- hide-non-public-information [application]
  (-> application
      hide-invitation-tokens
      ;; these are not used by the UI, so no need to expose them (especially the user IDs)
      (dissoc ::latest-comment-request-by-user ::latest-decision-request-by-user)
      (dissoc :application/past-members)))

(defn- personalize-todo [application user-id]
  (cond-> application
    (contains? (::latest-comment-request-by-user application) user-id)
    (assoc :application/todo :waiting-for-your-review)

    (contains? (::latest-decision-request-by-user application) user-id)
    (assoc :application/todo :waiting-for-your-decision)))

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
          (assoc :application/permissions permissions)
          (assoc :application/roles roles)
          (permissions/cleanup)))))
