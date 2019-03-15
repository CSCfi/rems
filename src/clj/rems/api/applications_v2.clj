(ns rems.api.applications-v2
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [medley.core :refer [map-vals]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.permissions :as permissions]
            [rems.workflow.dynamic :as dynamic]))

;;;; v2 API, pure application state based on application events

(defmulti ^:private event-type-specific-application-view
  "See `application-view`"
  (fn [_application event] (:event/type event)))

(defmethod event-type-specific-application-view :application.event/created
  [application event]
  (-> application
      (assoc :application/id (:application/id event)
             :application/created (:event/time event)
             :application/modified (:event/time event)
             :application/applicant (:event/actor event)
             :application/members #{}
             :application/resources (map (fn [resource]
                                           {:catalogue-item/id (:catalogue-item/id resource)
                                            :resource/ext-id (:resource/ext-id resource)})
                                         (:application/resources event))
             :application/licenses (map (fn [license]
                                          {:license/id (:license/id license)
                                           :license/accepted false})
                                        (:application/licenses event))
             :application/events []
             :application/form {:form/id (:form/id event)}
             :application/workflow {:workflow/id (:workflow/id event)
                                    :workflow/type (:workflow/type event)
                                    ;; TODO: other workflows
                                    ;; TODO: extract an event handler for dynamic workflow specific stuff
                                    :workflow.dynamic/state :rems.workflow.dynamic/draft
                                    :workflow.dynamic/handlers (:workflow.dynamic/handlers event)
                                    :workflow.dynamic/awaiting-commenters #{}
                                    :workflow.dynamic/awaiting-deciders #{}
                                    :workflow.dynamic/invitations {}})))

(defn- set-accepted-licences [licenses accepted-licenses]
  (map (fn [license]
         (assoc license :license/accepted (contains? accepted-licenses (:license/id license))))
       licenses))

(defmethod event-type-specific-application-view :application.event/draft-saved
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (assoc ::draft-answers (:application/field-values event))
      (update :application/licenses set-accepted-licences (:application/accepted-licenses event))))

(defmethod event-type-specific-application-view :application.event/member-invited
  [application event]
  (-> application
      (update-in [:application/workflow :workflow.dynamic/invitations] assoc (:invitation/token event) (:application/member event))))

(defmethod event-type-specific-application-view :application.event/member-uninvited
  [application event]
  (-> application
      (update-in [:application/workflow :workflow.dynamic/invitations] (fn [invitations]
                                                                         (->> invitations
                                                                              (remove (fn [[_token member]]
                                                                                        (= member (:application/member event))))
                                                                              (into {}))))))

(defmethod event-type-specific-application-view :application.event/member-joined
  [application event]
  (-> application
      (update :application/members conj {:userid (:event/actor event)})
      (update-in [:application/workflow :workflow.dynamic/invitations] dissoc (:invitation/token event))))

(defmethod event-type-specific-application-view :application.event/member-added
  [application event]
  (-> application
      (update :application/members conj (:application/member event))))

(defmethod event-type-specific-application-view :application.event/member-removed
  [application event]
  (-> application
      (update :application/members disj (:application/member event))))

(defmethod event-type-specific-application-view :application.event/submitted
  [application event]
  (-> application
      (assoc ::previous-submitted-answers (::submitted-answers application))
      (assoc ::submitted-answers (::draft-answers application))
      (dissoc ::draft-answers)
      (assoc-in [:application/workflow :workflow.dynamic/state] ::dynamic/submitted)))

(defmethod event-type-specific-application-view :application.event/returned
  [application event]
  (-> application
      (assoc-in [:application/workflow :workflow.dynamic/state] ::dynamic/returned)))

(defmethod event-type-specific-application-view :application.event/comment-requested
  [application event]
  (-> application
      (update-in [:application/workflow :workflow.dynamic/awaiting-commenters] set/union (set (:application/commenters event)))))

(defmethod event-type-specific-application-view :application.event/commented
  [application event]
  (-> application
      (update-in [:application/workflow :workflow.dynamic/awaiting-commenters] disj (:event/actor event))))

(defmethod event-type-specific-application-view :application.event/decision-requested
  [application event]
  (-> application
      (update-in [:application/workflow :workflow.dynamic/awaiting-deciders] set/union (set (:application/deciders event)))))

(defmethod event-type-specific-application-view :application.event/decided
  [application event]
  (-> application
      (update-in [:application/workflow :workflow.dynamic/awaiting-deciders] disj (:event/actor event))))

(defmethod event-type-specific-application-view :application.event/approved
  [application event]
  (-> application
      (assoc-in [:application/workflow :workflow.dynamic/state] ::dynamic/approved)))

(defmethod event-type-specific-application-view :application.event/rejected
  [application event]
  (-> application
      (assoc-in [:application/workflow :workflow.dynamic/state] ::dynamic/rejected)))

(defmethod event-type-specific-application-view :application.event/closed
  [application event]
  (-> application
      (assoc-in [:application/workflow :workflow.dynamic/state] ::dynamic/closed)))

(deftest test-event-type-specific-application-view
  (testing "supports all event types"
    (is (= (set (keys dynamic/event-schemas))
           (set (keys (methods event-type-specific-application-view)))))))

(defn- assert-same-application-id [application event]
  (assert (= (:application/id application)
             (:application/id event))
          (str "event for wrong application "
               "(not= " (:application/id application) " " (:application/id event) ")"))
  application)

;; TODO: replace rems.workflow.dynamic/apply-event with this
;;       (it will couple the write and read models, but it's probably okay
;;        because they both are about a single application and are logically coupled)
(defn application-view
  "Projection for the current state of a single application.
  Pure function; must use `enrich-with-injections` to enrich the model with
  data from other entities."
  [application event]
  (-> application
      (event-type-specific-application-view event)
      (dynamic/calculate-permissions event)
      (assert-same-application-id event)
      (assoc :application/last-activity (:event/time event))
      (update :application/events conj event)))

;;; v2 API, external entities (form, resources, licenses etc.)

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
    (concat merged-in-order orphans-in-order)))

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

(defn- enrich-form [app-form get-form]
  (let [form (get-form (:form/id app-form))
        app-fields (:form/fields app-form)
        rich-fields (map (fn [item]
                           {:field/id (:id item)
                            :field/value "" ; default for new forms
                            :field/type (keyword (:type item))
                            :field/title (localization-for :title item)
                            :field/placeholder (localization-for :inputprompt item)
                            :field/optional (:optional item)
                            :field/options (:options item)
                            :field/max-length (:maxlength item)})
                         (:items form))
        fields (merge-lists-by :field/id rich-fields app-fields)]
    (assoc app-form
           :form/title (:title form)
           :form/fields fields)))

(defn- set-application-description [application]
  (let [fields (get-in application [:application/form :form/fields])
        description (->> fields
                         (filter #(= :description (:field/type %)))
                         first
                         :field/value)]
    (assoc application :application/description description)))

(defn- enrich-resources [app-resources get-catalogue-item]
  (->> app-resources
       (map :catalogue-item/id)
       (map get-catalogue-item)
       (map (fn [item]
              {:catalogue-item/id (:id item)
               :resource/id (:resource-id item)
               :resource/ext-id (:resid item)
               :catalogue-item/title (assoc (localization-for :title item)
                                            :default (:title item))
               ;; TODO: remove unused keys
               :catalogue-item/start (:start item)
               :catalogue-item/end (:end item)
               :catalogue-item/enabled (:enabled item)
               :catalogue-item/archived (:archived item)
               :catalogue-item/state (keyword (:state item))}))
       (sort-by :catalogue-item/id)))

(defn- enrich-licenses [app-licenses get-license]
  (let [rich-licenses (->> app-licenses
                           (map :license/id)
                           (map get-license)
                           (map (fn [license]
                                  (let [license-type (keyword (:licensetype license))]
                                    (merge {:license/id (:id license)
                                            :license/type license-type
                                            :license/title (assoc (localization-for :title license)
                                                                  :default (:title license))
                                            ;; TODO: remove unused keys
                                            :license/start (:start license)
                                            :license/end (:end license)
                                            :license/enabled (:enabled license)
                                            :license/archived (:archived license)}
                                           (case license-type
                                             :text {:license/text (assoc (localization-for :textcontent license)
                                                                         :default (:textcontent license))}
                                             :link {:license/link (assoc (localization-for :textcontent license)
                                                                         :default (:textcontent license))}
                                             :attachment {:license/attachment-id (:attachment-id license)})))))

                           (sort-by :license/id))]
    (merge-lists-by :license/id rich-licenses app-licenses)))

(defn- enrich-with-injections [application {:keys [get-form get-catalogue-item get-license get-user]}]
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
        (update :application/form enrich-form get-form)
        set-application-description
        (update :application/resources enrich-resources get-catalogue-item)
        (update :application/licenses enrich-licenses get-license)
        (assoc :application/applicant-attributes (get-user (:application/applicant application))))))

(defn build-application-view [events injections]
  (-> (reduce application-view nil events)
      (enrich-with-injections injections)))

(defn- get-form [form-id]
  (-> (form/get-form form-id)
      (select-keys [:id :organization :title :start :end])
      (assoc :items (->> (db/get-form-items {:id form-id})
                         (mapv #(applications/process-item nil form-id %))))))

(defn- get-catalogue-item [catalogue-item-id]
  (assert (int? catalogue-item-id)
          (pr-str catalogue-item-id))
  (first (applications/get-catalogue-items [catalogue-item-id])))

(defn- get-license [license-id]
  (licenses/get-license license-id))

(defn- get-user [user-id]
  (users/get-user-attributes user-id))

(def ^:private injections {:get-form get-form
                           :get-catalogue-item get-catalogue-item
                           :get-license get-license
                           :get-user get-user})

(defn- hide-sensitive-information [application]
  (-> application
      (update :application/events dynamic/hide-sensitive-dynamic-events)
      (update :application/workflow dissoc :workflow.dynamic/handlers)))

(defn- hide-very-sensitive-information [application]
  (-> application
      (update-in [:application/workflow :workflow.dynamic/invitations] vals))) ; the keys are invitation tokens

(defn apply-user-permissions [application user-id]
  (let [see-application? (dynamic/see-application? application user-id)
        permissions (permissions/user-permissions application user-id)
        see-everything? (contains? permissions :see-everything)]
    (when see-application?
      (-> (if see-everything?
            application
            (hide-sensitive-information application))
          (hide-very-sensitive-information)
          (assoc :application/permissions permissions)
          (permissions/cleanup)))))

(defn api-get-application-v2 [user-id application-id]
  (let [events (applications/get-dynamic-application-events application-id)]
    (when (not (empty? events))
      (-> (build-application-view events injections)
          (apply-user-permissions user-id)))))

;;; v1 API compatibility layer

(defn- assoc-derived-data [user-id application]
  (assoc application
         :can-approve? (applications/can-approve? user-id application)
         :can-close? (applications/can-close? user-id application)
         :can-withdraw? (applications/can-withdraw? user-id application)
         :can-third-party-review? (applications/can-third-party-review? user-id application)
         :is-applicant? (applications/is-applicant? user-id application)))

(defn- transform-v2-to-v1 [application user-id]
  (let [form (:application/form application)
        workflow (:application/workflow application)
        catalogue-items (map (fn [resource]
                               (applications/translate-catalogue-item
                                {:id (:catalogue-item/id resource)
                                 :resource-id (:resource/id resource)
                                 :resid (:resource/ext-id resource)
                                 :wfid (:workflow/id workflow)
                                 :formid (:form/id form)
                                 :start (:catalogue-item/start resource)
                                 :end (:catalogue-item/end resource)
                                 :state (name (:catalogue-item/state resource))
                                 :archived (:catalogue-item/archived resource)
                                 :enabled (:catalogue-item/enabled resource)
                                 :title (:default (:catalogue-item/title resource))
                                 :localizations (into {} (for [lang (-> (set (keys (:catalogue-item/title resource)))
                                                                        (disj :default))]
                                                           [lang {:title (get-in resource [:catalogue-item/title lang])
                                                                  :langcode lang
                                                                  :id (:catalogue-item/id resource)}]))}))
                             (:application/resources application))]
    {:id (:form/id form)
     :title (:form/title form)
     :catalogue-items catalogue-items
     :applicant-attributes (:application/applicant-attributes application)
     :application (assoc-derived-data
                   user-id
                   {:id (:application/id application)
                    :formid (:form/id form)
                    :wfid (:workflow/id workflow)
                    :applicantuserid (:application/applicant application)
                    :members [{:userid (:application/applicant application)}]
                    :invited-members (:workflow.dynamic/invitations workflow)
                    :commenters (:workflow.dynamic/awaiting-commenters workflow)
                    :deciders (:workflow.dynamic/awaiting-deciders workflow)
                    :start (:application/created application)
                    :last-modified (:application/last-activity application)
                    :state (:workflow.dynamic/state workflow) ; TODO: round-based workflows
                    :description (:application/description application)
                    :catalogue-items catalogue-items
                    :form-contents {:items (into {} (for [field (:form/fields form)]
                                                      [(:field/id field) (:field/value field)]))
                                    :licenses (into {} (for [license (:application/licenses application)]
                                                         (when (:license/accepted license)
                                                           [(:license/id license) "approved"])))}
                    :submitted-form-contents nil ; TODO: not used in the UI, so not needed?
                    :previous-submitted-form-contents nil ; TODO: not used in the UI, so not needed?
                    :events [] ; TODO: round-based workflows
                    :dynamic-events (:application/events application)
                    :workflow {:type (:workflow/type workflow)
                               ;; TODO: add :handlers only when it exists? https://stackoverflow.com/a/16375390
                               :handlers (vec (:workflow.dynamic/handlers workflow))}
                    :possible-commands (:application/permissions application)
                    :fnlround 0 ; TODO: round-based workflows
                    :review-type nil}) ; TODO: round-based workflows
     :phases (applications/get-application-phases (:workflow.dynamic/state workflow))
     :licenses (map (fn [license]
                      {:id (:license/id license)
                       :type "license"
                       :licensetype (name (:license/type license))
                       ;; TODO: Licenses have three different start times: license.start, workflow_licenses.start, resource_licenses.start
                       ;;       (also catalogue_item_application_licenses.start but that table looks unused)
                       ;;       The old API returns either workflow_licenses.start or resource_licenses.start,
                       ;;       the new one returns license.start for now. Should we keep all three or simplify?
                       :start (:license/start license)
                       :end (:license/end license)
                       :enabled (:license/enabled license)
                       :archived (:license/archived license)
                       :approved (:license/accepted license)
                       :title (:default (:license/title license))
                       :textcontent (:default (or (:license/link license)
                                                  (:license/text license)))
                       :attachment-id (:license/attachment-id license)
                       :localizations (into {} (for [lang (-> (set (concat (keys (:license/title license))
                                                                           (keys (:license/link license))
                                                                           (keys (:license/text license))))
                                                              (disj :default))]
                                                 [lang {:title (get-in license [:license/title lang])
                                                        :textcontent (or (get-in license [:license/link lang])
                                                                         (get-in license [:license/text lang]))}]))})
                    (:application/licenses application))
     :items (map (fn [field]
                   {:id (:field/id field)
                    :type (name (:field/type field))
                    :optional (:field/optional field)
                    :options (:field/options field)
                    :maxlength (:field/max-length field)
                    :value (:field/value field)
                    :previous-value (:field/previous-value field)
                    :localizations (into {} (for [lang (set (concat (keys (:field/title field))
                                                                    (keys (:field/placeholder field))))]
                                              [lang {:title (get-in field [:field/title lang])
                                                     :inputprompt (get-in field [:field/placeholder lang])}]))})
                 (:form/fields form))}))

(defn api-get-application-v1 [user-id application-id]
  (when-let [v2 (api-get-application-v2 user-id application-id)]
    (transform-v2-to-v1 v2 user-id)))

;;; v2 API, listing all applications

(defn- all-applications-view
  "Projection for the current state of all applications."
  [applications event]
  (if-let [app-id (:application/id event)] ; old style events don't have :application/id
    (update applications app-id application-view event)
    applications))

(defn- exclude-unnecessary-keys-from-summary [application]
  (dissoc application
          :application/events
          :application/form
          :application/licenses))

(defn get-user-applications-v2 [user-id]
  ;; TODO: cache the applications and build the projection incrementally as new events are published
  (let [events (applications/get-dynamic-application-events-since 0)
        applications (reduce all-applications-view nil events)]
    (->> (vals applications)
         (map #(apply-user-permissions % user-id))
         (remove nil?)
         ;; TODO: for caching it may be necessary to make assoc-injections idempotent and consider cache invalidation
         (map #(enrich-with-injections % injections))
         (map exclude-unnecessary-keys-from-summary))))
