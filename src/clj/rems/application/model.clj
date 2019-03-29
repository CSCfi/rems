(ns rems.application.model
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [medley.core :refer [map-vals]]
            [rems.workflow.dynamic :as dynamic]))

(defmulti ^:private event-type-specific-application-view
  "See `application-view`"
  (fn [_application event] (:event/type event)))

(defmethod event-type-specific-application-view :application.event/created
  [application event]
  (-> application
      (assoc :application/id (:application/id event)
             :application/external-id (:application/external-id event)
             :application/state :application.state/draft
             :application/created (:event/time event)
             :application/modified (:event/time event)
             :application/applicant (:event/actor event)
             :application/members #{}
             :application/invitation-tokens {}
             :application/resources (map (fn [resource]
                                           {:catalogue-item/id (:catalogue-item/id resource)
                                            :resource/ext-id (:resource/ext-id resource)})
                                         (:application/resources event))
             :application/licenses (map (fn [license]
                                          {:license/id (:license/id license)
                                           :license/accepted false})
                                        (:application/licenses event))
             :application/accepted-licenses {(:event/actor event) #{}}
             :application/events []
             :application/form {:form/id (:form/id event)}
             :application/workflow {:workflow/id (:workflow/id event)
                                    :workflow/type (:workflow/type event)
                                    ;; TODO: other workflows
                                    ;; TODO: extract an event handler for dynamic workflow specific stuff
                                    :workflow.dynamic/handlers (:workflow.dynamic/handlers event)
                                    :workflow.dynamic/awaiting-commenters #{}
                                    :workflow.dynamic/awaiting-deciders #{}})))

(defn- set-accepted-licences [licenses accepted-licenses]
  (map (fn [license]
         (assoc license :license/accepted (contains? accepted-licenses (:license/id license))))
       licenses))

(defmethod event-type-specific-application-view :application.event/draft-saved
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (assoc ::draft-answers (:application/field-values event))
      (update :application/licenses set-accepted-licences (:application/accepted-licenses event))
      (assoc-in [:application/accepted-licenses (:event/actor event)] (:application/accepted-licenses event))))

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
      (update :application/members disj (:application/member event))))

(defmethod event-type-specific-application-view :application.event/submitted
  [application event]
  (-> application
      (assoc ::previous-submitted-answers (::submitted-answers application))
      (assoc ::submitted-answers (::draft-answers application))
      (dissoc ::draft-answers)
      (assoc :application/state :application.state/submitted)))

(defmethod event-type-specific-application-view :application.event/returned
  [application event]
  (-> application
      (assoc ::draft-answers (::submitted-answers application)) ; guard against re-submit without saving a new draft
      (assoc :application/state :application.state/returned)))

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
      (assoc :application/state :application.state/approved)))

(defmethod event-type-specific-application-view :application.event/rejected
  [application event]
  (-> application
      (assoc :application/state :application.state/rejected)))

(defmethod event-type-specific-application-view :application.event/closed
  [application event]
  (-> application
      (assoc :application/state :application.state/closed)))

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
    (assoc application :application/description (str description))))

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
               :catalogue-item/archived (:archived item)}))
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
                                             :attachment {:license/attachment-id (assoc (localization-for :attachment-id license)
                                                                                        :default (:attachment-id license))
                                                          ;; TODO: remove filename as unused?
                                                          :license/attachment-filename (assoc (localization-for :textcontent license)
                                                                                              :default (:textcontent license))})))))
                           (sort-by :license/id))]
    (merge-lists-by :license/id rich-licenses app-licenses)))

(defn enrich-with-injections [application {:keys [get-form get-catalogue-item get-license get-user]}]
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
