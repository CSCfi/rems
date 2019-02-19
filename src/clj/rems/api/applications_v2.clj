(ns rems.api.applications-v2
  (:require [clojure.test :refer [deftest is testing]]
            [medley.core :refer [map-vals]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.permissions :as permissions]
            [rems.workflow.dynamic :as dynamic])
  (:import (org.joda.time DateTime)))

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
             :application/resources (map (fn [resource]
                                           {:catalogue-item/id (:catalogue-item/id resource)
                                            :resource/ext-id (:resource/ext-id resource)})
                                         (:application/resources event))
             :application/licenses (map (fn [license]
                                          {:license/id (:license/id license)
                                           :license/accepted false})
                                        (:application/licenses event))
             :application/events []
             :application/form {:form/id (:form/id event)
                                :form/fields []}
             :application/workflow {:workflow/id (:workflow/id event)
                                    :workflow/type (:workflow/type event)
                                    ;; TODO: other workflows
                                    :workflow.dynamic/state :rems.workflow.dynamic/draft
                                    :workflow.dynamic/handlers (:workflow.dynamic/handlers event)})))

(defn- set-accepted-licences [licenses accepted-licenses]
  (map (fn [license]
         (assoc license :license/accepted (contains? accepted-licenses (:license/id license))))
       licenses))

(defmethod event-type-specific-application-view :application.event/draft-saved
  [application event]
  (-> application
      (assoc :application/modified (:event/time event))
      (assoc-in [:application/form :form/fields] (map (fn [[field-id value]]
                                                        {:field/id field-id
                                                         :field/value value})
                                                      (:application/field-values event)))
      (update :application/licenses set-accepted-licences (:application/accepted-licenses event))))

(defmethod event-type-specific-application-view :application.event/member-invited
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/member-added
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/member-removed
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/member-uninvited
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/submitted
  [application event]
  (-> application
      (assoc-in [:application/workflow :workflow.dynamic/state] ::dynamic/submitted)))

(defmethod event-type-specific-application-view :application.event/returned
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/comment-requested
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/commented
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/decision-requested
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/decided
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/approved
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/rejected
  [application event]
  application)

(defmethod event-type-specific-application-view :application.event/closed
  [application event]
  application)

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
(defn- application-view
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
               :catalogue-item/start (:start item)
               :catalogue-item/state (keyword (:state item))}))
       (sort-by :catalogue-item/id)))

(defn- enrich-licenses [app-licenses get-license]
  (let [rich-licenses (->> app-licenses
                           (map :license/id)
                           (map get-license)
                           (map (fn [license]
                                  (let [type (keyword (:licensetype license))
                                        content-key (case type
                                                      :link :license/link
                                                      :text :license/text)]
                                    {:license/id (:id license)
                                     :license/type type
                                     :license/start (:start license)
                                     :license/end (:end license)
                                     :license/title (assoc (localization-for :title license)
                                                           :default (:title license))
                                     content-key (assoc (localization-for :textcontent license)
                                                        :default (:textcontent license))})))
                           (sort-by :license/id))]
    (merge-lists-by :license/id rich-licenses app-licenses)))

(defn- enrich-with-injections [application {:keys [get-form get-catalogue-item get-license get-user]}]
  (-> application
      (update :application/form enrich-form get-form)
      set-application-description
      (update :application/resources enrich-resources get-catalogue-item)
      (update :application/licenses enrich-licenses get-license)
      (assoc :application/applicant-attributes (get-user (:application/applicant application)))))

(defn- build-application-view [events injections]
  (-> (reduce application-view nil events)
      (enrich-with-injections injections)))

(defn- valid-events [events]
  (doseq [event events]
    (applications/validate-dynamic-event event))
  events)

(deftest test-application-view
  (let [injections {:get-form {40 {:id 40
                                   :organization "org"
                                   :title "form title"
                                   :start (DateTime. 100)
                                   :end nil
                                   :items [{:id 41
                                            :localizations {:en {:title "en title"
                                                                 :inputprompt "en placeholder"}
                                                            :fi {:title "fi title"
                                                                 :inputprompt "fi placeholder"}}
                                            :optional false
                                            :options []
                                            :maxlength 100
                                            :type "description"}
                                           {:id 42
                                            :localizations {:en {:title "en title"
                                                                 :inputprompt "en placeholder"}
                                                            :fi {:title "fi title"
                                                                 :inputprompt "fi placeholder"}}
                                            :optional false
                                            :options []
                                            :maxlength 100
                                            :type "text"}]}}

                    :get-catalogue-item {10 {:id 10
                                             :resource-id 11
                                             :resid "urn:11"
                                             :wfid 50
                                             :formid 40
                                             :title "non-localized title"
                                             :localizations {:en {:id 10
                                                                  :langcode :en
                                                                  :title "en title"}
                                                             :fi {:id 10
                                                                  :langcode :fi
                                                                  :title "fi title"}}
                                             :start (DateTime. 100)
                                             :state "enabled"}
                                         20 {:id 20
                                             :resource-id 21
                                             :resid "urn:21"
                                             :wfid 50
                                             :formid 40
                                             :title "non-localized title"
                                             :localizations {:en {:id 20
                                                                  :langcode :en
                                                                  :title "en title"}
                                                             :fi {:id 20
                                                                  :langcode :fi
                                                                  :title "fi title"}}
                                             :start (DateTime. 100)
                                             :state "enabled"}}

                    :get-license {30 {:id 30
                                      :licensetype "link"
                                      :start (DateTime. 100)
                                      :end nil
                                      :title "non-localized title"
                                      :textcontent "http://non-localized-license-link"
                                      :localizations {:en {:title "en title"
                                                           :textcontent "http://en-license-link"}
                                                      :fi {:title "fi title"
                                                           :textcontent "http://fi-license-link"}}}
                                  31 {:id 31
                                      :licensetype "text"
                                      :start (DateTime. 100)
                                      :end nil
                                      :title "non-localized title"
                                      :textcontent "non-localized license text"
                                      :localizations {:en {:title "en title"
                                                           :textcontent "en license text"}
                                                      :fi {:title "fi title"
                                                           :textcontent "fi license text"}}}}

                    :get-user {"applicant" {:eppn "applicant"
                                            :mail "applicant@example.com"
                                            :commonName "Applicant"}}}
        apply-events (fn [events]
                       (permissions/cleanup
                        (build-application-view
                         (valid-events events)
                         injections)))

        created-event {:event/type :application.event/created
                       :event/time (DateTime. 1000)
                       :event/actor "applicant"
                       :application/id 1
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses [{:license/id 30}
                                              {:license/id 31}]
                       :form/id 40
                       :workflow/id 50
                       :workflow/type :workflow/dynamic
                       :workflow.dynamic/handlers #{"handler"}}

        expected-new-application {:application/id 1
                                  :application/created (DateTime. 1000)
                                  :application/modified (DateTime. 1000)
                                  :application/last-activity (DateTime. 1000)
                                  :application/applicant "applicant"
                                  :application/applicant-attributes {:eppn "applicant"
                                                                     :mail "applicant@example.com"
                                                                     :commonName "Applicant"}
                                  :application/resources [{:catalogue-item/id 10
                                                           :resource/id 11
                                                           :resource/ext-id "urn:11"
                                                           :catalogue-item/title {:en "en title"
                                                                                  :fi "fi title"
                                                                                  :default "non-localized title"}
                                                           :catalogue-item/start (DateTime. 100)
                                                           :catalogue-item/state :enabled}
                                                          {:catalogue-item/id 20
                                                           :resource/id 21
                                                           :resource/ext-id "urn:21"
                                                           :catalogue-item/title {:en "en title"
                                                                                  :fi "fi title"
                                                                                  :default "non-localized title"}
                                                           :catalogue-item/start (DateTime. 100)
                                                           :catalogue-item/state :enabled}]
                                  :application/licenses [{:license/id 30
                                                          :license/accepted false
                                                          :license/type :link
                                                          :license/start (DateTime. 100)
                                                          :license/end nil
                                                          :license/title {:en "en title"
                                                                          :fi "fi title"
                                                                          :default "non-localized title"}
                                                          :license/link {:en "http://en-license-link"
                                                                         :fi "http://fi-license-link"
                                                                         :default "http://non-localized-license-link"}}
                                                         {:license/id 31
                                                          :license/accepted false
                                                          :license/type :text
                                                          :license/start (DateTime. 100)
                                                          :license/end nil
                                                          :license/title {:en "en title"
                                                                          :fi "fi title"
                                                                          :default "non-localized title"}
                                                          :license/text {:en "en license text"
                                                                         :fi "fi license text"
                                                                         :default "non-localized license text"}}]
                                  :application/events [created-event]
                                  :application/description ""
                                  :application/form {:form/id 40
                                                     :form/title "form title"
                                                     :form/fields [{:field/id 41
                                                                    :field/value ""
                                                                    :field/type :description
                                                                    :field/title {:en "en title" :fi "fi title"}
                                                                    :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                                                    :field/optional false
                                                                    :field/options []
                                                                    :field/max-length 100}
                                                                   {:field/id 42
                                                                    :field/value ""
                                                                    :field/type :text
                                                                    :field/title {:en "en title" :fi "fi title"}
                                                                    :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                                                    :field/optional false
                                                                    :field/options []
                                                                    :field/max-length 100}]}
                                  :application/workflow {:workflow/id 50
                                                         :workflow/type :workflow/dynamic
                                                         :workflow.dynamic/state :rems.workflow.dynamic/draft
                                                         :workflow.dynamic/handlers #{"handler"}}}]

    (testing "new application"
      (is (= expected-new-application
             (apply-events [created-event]))))

    (testing "draft saved"
      (let [draft-saved-event {:event/type :application.event/draft-saved
                               :event/time (DateTime. 2000)
                               :event/actor "applicant"
                               :application/id 1
                               :application/field-values {41 "foo"
                                                          42 "bar"}
                               :application/accepted-licenses #{30 31}}]
        (is (= (-> expected-new-application
                   (assoc-in [:application/modified] (DateTime. 2000))
                   (assoc-in [:application/last-activity] (DateTime. 2000))
                   (assoc-in [:application/events] [created-event draft-saved-event])
                   (assoc-in [:application/licenses 0 :license/accepted] true)
                   (assoc-in [:application/licenses 1 :license/accepted] true)
                   (assoc-in [:application/description] "foo")
                   (assoc-in [:application/form :form/fields 0 :field/value] "foo")
                   (assoc-in [:application/form :form/fields 1 :field/value] "bar"))
               (apply-events [created-event
                              draft-saved-event])))))

    (testing "submitted"
      (let [submitted-event {:event/type :application.event/submitted
                             :event/time (DateTime. 2000)
                             :event/actor "applicant"
                             :application/id 1}]
        (is (= (-> expected-new-application
                   (assoc-in [:application/last-activity] (DateTime. 2000))
                   (assoc-in [:application/events] [created-event submitted-event])
                   (assoc-in [:application/workflow :workflow.dynamic/state] ::dynamic/submitted))
               (apply-events [created-event
                              submitted-event])))))))

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

(defn- apply-user-permissions [application user-id]
  (let [roles (permissions/user-roles application user-id)
        permissions (permissions/user-permissions application user-id)
        see-everything? (contains? permissions :see-everything)]
    (when (not (empty? roles))
      (-> (if see-everything?
            application
            (hide-sensitive-information application))
          (assoc :application/permissions permissions)
          (permissions/cleanup)))))

(deftest test-apply-user-permissions
  (let [application (-> (application-view nil {:event/type :application.event/created
                                               :event/actor "applicant"
                                               :workflow.dynamic/handlers #{"handler"}})
                        (permissions/give-role-to-users :role-1 ["user-1"])
                        (permissions/give-role-to-users :role-2 ["user-2"])
                        (permissions/set-role-permissions {:role-1 []
                                                           :role-2 [:foo :bar]}))]
    (testing "users with a role can see the application"
      (is (not (nil? (apply-user-permissions application "user-1")))))
    (testing "users without a role cannot see the application"
      (is (nil? (apply-user-permissions application "user-3"))))
    (testing "lists the user's permissions"
      (is (= #{} (:application/permissions (apply-user-permissions application "user-1"))))
      (is (= #{:foo :bar} (:application/permissions (apply-user-permissions application "user-2")))))

    (let [all-events [{:event/type :application.event/created}
                      {:event/type :application.event/submitted}
                      {:event/type :application.event/comment-requested}]
          restricted-events [{:event/type :application.event/created}
                             {:event/type :application.event/submitted}]
          application (-> application
                          (assoc :application/events all-events)
                          (permissions/set-role-permissions {:role-1 [:see-everything]}))]
      (testing "privileged users"
        (let [application (apply-user-permissions application "user-1")]
          (testing "see all events"
            (is (= all-events
                   (:application/events application))))
          (testing "see dynamic workflow handlers"
            (is (= #{"handler"}
                   (get-in application [:application/workflow :workflow.dynamic/handlers]))))))

      (testing "normal users"
        (let [application (apply-user-permissions application "user-2")]
          (testing "see only some events"
            (is (= restricted-events
                   (:application/events application))))
          (testing "don't see dynamic workflow handlers"
            (is (= nil
                   (get-in application [:application/workflow :workflow.dynamic/handlers])))))))))

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
                                 :state (name (:catalogue-item/state resource))
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
                       :approved (:license/accepted license)
                       :title (:default (:license/title license))
                       :textcontent (:default (or (:license/link license)
                                                  (:license/text license)))
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
                    :previous-value nil ; TODO
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
