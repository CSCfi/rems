(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.java.jdbc :as jdbc]
            [clojure.test :refer [deftest is]]
            [conman.core :as conman]
            [cprop.tools :refer [merge-maps]]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.db.workflow-actors :as actors]
            [rems.form-validation :as form-validation]
            [rems.json :as json]
            [rems.permissions :as permissions]
            [rems.util :refer [secure-token]]
            [rems.workflow.dynamic :as dynamic]
            [schema-tools.core :as st]
            [schema.coerce :as coerce]
            [schema.utils])
  (:import [org.joda.time DateTime]))

(declare get-dynamic-application-state)

(defn draft?
  "Is the given `application-id` for an unsaved draft application?"
  [application-id]
  (nil? application-id))

(declare get-application-state)

(defn- not-empty? [args]
  ((complement empty?) args))

;;; Query functions

(declare is-dynamic-application?)

(declare fix-workflow-from-db)
(declare is-dynamic-handler?)

(defn- is-actor? [user-id actors]
  (assert user-id)
  (assert actors)
  (contains? (set actors) user-id))

(defn can-act-as?
  [user-id application role]
  (assert user-id)
  (assert application)
  (assert role)
  (or (and (= "applied" (:state application))
           (is-actor? user-id (actors/get-by-role (:id application) (:curround application) role)))
      (and (= "approver" role)
           (contains? (dynamic/possible-commands user-id (get-application-state (:id application)))
                      :application.command/approve))))

(declare get-application-state)

(defn- has-actor-role? [user-id application-id role]
  (assert user-id)
  (assert application-id)
  (assert role)
  (or (is-actor? user-id (actors/get-by-role application-id role))
      (is-dynamic-handler? user-id (get-application-state application-id))))

(defn can-approve? [user-id application]
  (assert user-id)
  (assert application)
  (can-act-as? user-id application "approver"))

(defn- is-approver? [user-id application-id]
  (assert user-id)
  (assert application-id)
  (has-actor-role? user-id application-id "approver"))

(defn can-review? [user-id application]
  (assert user-id)
  (assert application)
  (can-act-as? user-id application "reviewer"))

(defn- is-reviewer? [user-id application-id]
  (has-actor-role? user-id application-id "reviewer"))

(defn- is-third-party-reviewer?
  "Checks if a given user has been requested to review the given application.
   Additionally a specific round can be provided to narrow the check to apply only to the given round."
  ([user application]
   (->> (:events application)
        (filter #(and (= "review-request" (:event %)) (= user (:userid %))))
        (not-empty?)))
  ([user round application]
   (is-third-party-reviewer? user (update application :events (fn [events] (filter #(= round (:round %)) events))))))

(defn can-third-party-review?
  "Checks if the current user can perform a 3rd party review action on the current round for the given application."
  [user-id application]
  (and (= "applied" (:state application))
       (is-third-party-reviewer? user-id (:curround application) application)))

(defn is-applicant? [user-id application]
  (assert user-id)
  (assert application)
  (= user-id (:applicantuserid application)))

(defn may-see-application? [user-id application]
  (assert user-id)
  (assert application)
  (let [application-id (:id application)]
    (or (is-applicant? user-id application)
        (is-approver? user-id application-id)
        (is-reviewer? user-id application-id)
        (is-third-party-reviewer? user-id application)
        (model/see-application? application user-id))))

(defn can-close? [user-id application]
  (assert user-id)
  (assert application)
  (let [application-id (:id application)]
    (or (and (is-approver? user-id application-id)
             (= "approved" (:state application)))
        (and (is-applicant? user-id application)
             (not= "closed" (:state application))))))

(defn can-withdraw? [user-id application]
  (assert user-id)
  (assert application)
  (and (is-applicant? user-id application)
       (= (:state application) "applied")))

;;; Form & field stuff
;; TODO these should be in rems.db.form or so

(defn- get-field-value [field form-id application-id]
  (let [query-params {:item (:id field)
                      :form form-id
                      :application application-id}]
    (if (= "attachment" (:type field))
      (:filename (db/get-attachment query-params))
      (:value (db/get-field-value query-params)))))

(defn- process-field-options [options]
  (->> options
       (map (fn [{:keys [key langcode label displayorder]}]
              {:key key
               :label {(keyword langcode) label}
               :displayorder displayorder}))
       (group-by :key)
       (map (fn [[_key options]] (apply merge-maps options))) ; merge label translations
       (sort-by :displayorder)
       (mapv #(select-keys % [:key :label]))))

(deftest process-field-options-test
  (is (= [{:key "yes" :label {:en "Yes" :fi "Kyllä"}}
          {:key "no" :label {:en "No" :fi "Ei"}}]
         (process-field-options
          [{:itemid 9, :key "no", :langcode "en", :label "No", :displayorder 1}
           {:itemid 9, :key "no", :langcode "fi", :label "Ei", :displayorder 1}
           {:itemid 9, :key "yes", :langcode "en", :label "Yes", :displayorder 0}
           {:itemid 9, :key "yes", :langcode "fi", :label "Kyllä", :displayorder 0}]))))

(defn process-field
  "Returns a field structure like this:

    {:id 123
     :type \"texta\"
     :title \"Item title\"
     :inputprompt \"hello\"
     :optional true
     :value \"filled value or nil\"}"
  [application-id form-id field]
  {:id (:id field)
   :optional (:formitemoptional field)
   :type (:type field)
   ;; TODO here we do a db call per item, for licenses we do one huge
   ;; db call. Not sure which is better?
   :localizations (into {} (for [{:keys [langcode title inputprompt]}
                                 (db/get-form-item-localizations {:item (:id field)})]
                             [(keyword langcode) {:title title :inputprompt inputprompt}]))
   :options (process-field-options (db/get-form-item-options {:item (:id field)}))
   :value (or
           (when-not (draft? application-id)
             (get-field-value field form-id application-id))
           "")
   :maxlength (:maxlength field)})

;;; Application phases

(defn get-application-phases [state]
  (cond (contains? #{"rejected" :application.state/rejected} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
         {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]

        (contains? #{"approved" :application.state/approved} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :approved? true :text :t.phases/approve}
         {:phase :result :completed? true :approved? true :text :t.phases/approved}]

        (contains? #{"closed" :application.state/closed} state)
        [{:phase :apply :closed? true :text :t.phases/apply}
         {:phase :approve :closed? true :text :t.phases/approve}
         {:phase :result :closed? true :text :t.phases/approved}]

        (contains? #{"draft" "returned" "withdrawn" :application.state/draft} state)
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        (contains? #{"applied" :application.state/submitted} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :active? true :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        :else
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]))



(defn create-new-draft [user-id wfid]
  (assert user-id)
  (assert wfid)
  (:id (db/create-application! {:user user-id :wfid wfid})))

(defn create-new-draft-at-time [user-id wfid time]
  (:id (db/create-application! {:user user-id :wfid wfid :start time})))

;;; Applying events

(defmulti ^:private apply-event
  "Applies an event to an application state."
  ;; dispatch by event type
  (fn [_application event] (:event event)))

(defn get-event-types
  "Fetch sequence of supported event names."
  []
  (keys (methods apply-event)))

(defmethod apply-event "save"
  [application _event]
  application)

(defmethod apply-event "apply"
  [application event]
  (assert (#{"draft" "returned" "withdrawn"} (:state application))
          (str "Can't submit application " (pr-str application)))
  (assert (= (:round event) 0)
          (str "Apply event should have round 0" (pr-str event)))
  (assoc application :state "applied" :curround 0))

(defn- apply-approve [application event]
  (assert (= (:state application) "applied")
          (str "Can't approve application " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and approval rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (if (= (:curround application) (:fnlround application))
    (assoc application :state "approved")
    (assoc application :state "applied" :curround (inc (:curround application)))))

(defmethod apply-event "approve"
  [application event]
  (apply-approve application event))

(defmethod apply-event "autoapprove"
  [application event]
  (apply-approve application event))

(defmethod apply-event "reject"
  [application event]
  (assert (= (:state application) "applied")
          (str "Can't reject application " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and rejection rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (assoc application :state "rejected"))

(defmethod apply-event "return"
  [application event]
  (assert (= (:state application) "applied")
          (str "Can't return application " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and rejection rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (assoc application :state "returned" :curround 0))

(defmethod apply-event "review"
  [application event]
  (assert (= (:state application) "applied")
          (str "Can't review application " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and review rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (if (= (:curround application) (:fnlround application))
    (assoc application :state "approved")
    (assoc application :state "applied" :curround (inc (:curround application)))))

(defmethod apply-event "third-party-review"
  [application event]
  (assert (= (:state application) "applied")
          (str "Can't review application " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and review rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (assoc application :state "applied"))

(defmethod apply-event "review-request"
  [application event]
  (assert (= (:state application) "applied")
          (str "Can't send a review request " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and review request rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (assoc application :state "applied"))

(defmethod apply-event "withdraw"
  [application event]
  (assert (= (:state application) "applied")
          (str "Can't withdraw application " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and withdrawal rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (assoc application :state "withdrawn" :curround 0))

(defmethod apply-event "close"
  [application event]
  (assoc application :state "closed"))

(defn- apply-events [application events]
  (reduce apply-event application events))

;;; Public event api

(defn get-application-state ; TODO: legacy code; remove me
  ([application-id]
   (get-application-state (first (db/get-applications {:id application-id}))
                          (map #(dissoc % :id :appid) ; remove keys not in v1 API
                               (db/get-application-events {:application application-id}))))
  ([application events]
   (if (not (nil? (:workflow application)))
     (get-dynamic-application-state (:id application))
     (let [application (-> application
                           (dissoc :workflow)
                           (assoc :state "draft" :curround 0) ;; reset state
                           (assoc :events events)
                           (assoc :last-modified (or (:time (last events))
                                                     (:start application))))]
       (apply-events application events)))))

(declare handle-state-change)

(defn try-autoapprove-application
  "If application can be autoapproved (round has no approvers), add an
   autoapprove event. Otherwise do nothing."
  [user-id application]
  (let [application-id (:id application)
        round (:curround application)
        fnlround (:fnlround application)
        state (:state application)]
    (when (= "applied" state)
      (let [approvers (actors/get-by-role application-id round "approver")
            reviewers (actors/get-by-role application-id round "reviewer")]
        (when (and (empty? approvers)
                   (empty? reviewers)
                   (<= round fnlround))
          (db/add-application-event! {:application application-id :user user-id
                                      :round round :event "autoapprove" :comment nil})
          true)))))

(defn handle-state-change [user-id application-id]
  (let [application (get-application-state application-id)]
    (entitlements/update-entitlements-for application)
    (when (try-autoapprove-application user-id application)
      (recur user-id application-id))))

(defn submit-application [applicant-id application-id]
  (assert applicant-id)
  (assert application-id)
  (let [application (get-application-state application-id)]
    (when-not (= applicant-id (:applicantuserid application))
      (throw-forbidden))
    (when-not (#{"draft" "returned" "withdrawn"} (:state application))
      (throw-forbidden))
    (db/add-application-event! {:application application-id :user applicant-id
                                :round 0 :event "apply" :comment nil})
    (handle-state-change applicant-id application-id)))

(defn- judge-application [approver-id application-id event round msg]
  (assert approver-id)
  (assert application-id)
  (assert event)
  (assert round)
  (assert msg)
  (let [state (get-application-state application-id)]
    (when-not (= round (:curround state))
      (throw-forbidden))
    (db/add-application-event! {:application application-id :user approver-id
                                :round round :event event :comment msg})
    (handle-state-change approver-id application-id)))

(defn approve-application [approver-id application-id round msg]
  (when-not (can-approve? approver-id (get-application-state application-id))
    (throw-forbidden))
  (judge-application approver-id application-id "approve" round msg))

(defn reject-application [user-id application-id round msg]
  (when-not (can-approve? user-id (get-application-state application-id))
    (throw-forbidden))
  (judge-application user-id application-id "reject" round msg))

(defn return-application [user-id application-id round msg]
  (when-not (can-approve? user-id (get-application-state application-id))
    (throw-forbidden))
  (judge-application user-id application-id "return" round msg))

(defn review-application [user-id application-id round msg]
  (when-not (can-review? user-id (get-application-state application-id))
    (throw-forbidden))
  (judge-application user-id application-id "review" round msg))

(defn perform-third-party-review [user-id application-id round msg]
  (let [application (get-application-state application-id)]
    (when-not (can-third-party-review? user-id application)
      (throw-forbidden))
    (when-not (= round (:curround application))
      (throw-forbidden))
    (db/add-application-event! {:application application-id :user user-id
                                :round round :event "third-party-review" :comment msg})))

(defn send-review-request [user-id application-id round msg recipients]
  (let [application (get-application-state application-id)]
    (when-not (can-approve? user-id application)
      (throw-forbidden))
    (when-not (= round (:curround application))
      (throw-forbidden))
    (assert (not-empty? recipients)
            (str "Can't send a review request without recipients."))
    (let [send-to (if (vector? recipients)
                    recipients
                    (vector recipients))]
      (doseq [recipient send-to]
        (when-not (is-third-party-reviewer? recipient (:curround application) application)
          (db/add-application-event! {:application application-id :user recipient
                                      :round round :event "review-request" :comment msg}))))))

;; TODO better name
;; TODO consider refactoring together with judge
(defn- unjudge-application
  "Action handling for both approver and applicant."
  [user-id application event round msg]
  (let [application-id (:id application)]
    (when-not (= round (:curround application))
      (throw-forbidden))
    (db/add-application-event! {:application application-id :user user-id
                                :round round :event event :comment msg})
    (handle-state-change user-id application-id)))

(defn withdraw-application [applicant-id application-id round msg]
  (let [application (get-application-state application-id)]
    (when-not (can-withdraw? applicant-id application)
      (throw-forbidden))
    (unjudge-application applicant-id application "withdraw" round msg)))

(defn close-application [user-id application-id round msg]
  (let [application (get-application-state application-id)]
    (when-not (can-close? user-id application)
      (throw-forbidden))
    (unjudge-application user-id application "close" round msg)))

;;; Dynamic workflows
;; TODO these should be in their own namespace probably

(defn- fix-workflow-from-db [wf]
  ;; TODO could use a schema for this coercion
  (update (json/parse-string wf)
          :type keyword))

(defn- datestring->datetime [s]
  (if (string? s)
    (time-format/parse s)
    s))

(def ^:private datestring-coercion-matcher
  {DateTime datestring->datetime})

(defn- coercion-matcher [schema]
  (or (datestring-coercion-matcher schema)
      (coerce/string-coercion-matcher schema)))

;; TODO: remove "dynamic" from names
(def ^:private coerce-dynamic-event-commons
  (coerce/coercer (st/open-schema events/EventBase) coercion-matcher))

(def ^:private coerce-dynamic-event-specifics
  (coerce/coercer events/Event coercion-matcher))

(defn- coerce-dynamic-event [event]
  ;; must coerce the common fields first, so that dynamic/Event can choose the right event schema based on the event type
  (-> event
      coerce-dynamic-event-commons
      coerce-dynamic-event-specifics))

(defn json->event [json]
  (when json
    (let [result (coerce-dynamic-event (json/parse-string json))]
      (when (schema.utils/error? result)
        ;; similar exception as what schema.core/validate throws
        (throw (ex-info (str "Value does not match schema: " (pr-str result))
                        {:schema events/Event :value json :error result})))
      result)))

(defn event->json [event]
  (events/validate-event event)
  (json/generate-string event))

(defn- fix-event-from-db [event]
  (assoc (-> event :eventdata json->event)
         :event/id (:id event)))

(defn get-application-events [application-id]
  (map fix-event-from-db (db/get-application-events {:application application-id})))

(defn get-all-events-since [event-id]
  (map fix-event-from-db (db/get-application-events-since {:id event-id})))

(defn get-dynamic-application-state [application-id] ; TODO: legacy code; remove me
  (let [application (or (first (db/get-applications {:id application-id}))
                        (throw (rems.InvalidRequestException.
                                (str "Application " application-id " not found"))))
        events (get-application-events application-id)
        application (assoc application
                           :state :application.state/draft
                           :dynamic-events events
                           :workflow (fix-workflow-from-db (:workflow application))
                           :last-modified (or (:event/time (last events))
                                              (:start application)))]
    (assert (is-dynamic-application? application) (pr-str application))
    (dynamic/apply-events application events)))

(defn add-event! [event]
  (db/add-application-event! {:application (:application/id event)
                              :user (:event/actor event)
                              :comment nil
                              :round -1
                              :event (str (:event/type event))
                              :eventdata (event->json event)})
  nil)

(defn allocate-external-id! [prefix]
  (conman/with-transaction [rems.db.core/*db* {:isolation :serializable}]
    (let [all (db/get-external-ids {:prefix prefix})
          last (apply max (cons 0 (map (comp read-string :suffix) all)))
          new (str (inc last))]
      (db/add-external-id! {:prefix prefix :suffix new})
      {:prefix prefix :suffix new})))

(defn format-external-id [{:keys [prefix suffix]}]
  (str prefix "/" suffix))

(defn application-external-id! [time]
  (let [id-prefix (str (.getYear time))]
    (format-external-id (allocate-external-id! id-prefix))))

(defn application-created-event [{:keys [application-id catalogue-item-ids time actor allocate-external-id?]}]
  (assert (seq catalogue-item-ids) "catalogue item not specified")
  (let [items (catalogue/get-localized-catalogue-items {:ids catalogue-item-ids})]
    (assert (= (count items) (count catalogue-item-ids)) "catalogue item not found")
    (assert (= 1 (count (distinct (mapv :wfid items)))) "catalogue items did not have the same workflow")
    (assert (= 1 (count (distinct (mapv :formid items)))) "catalogue items did not have the same form")
    (let [workflow-id (:wfid (first items))
          form-id (:formid (first items))
          workflow (-> (:workflow (workflow/get-workflow workflow-id))
                       (update :type keyword))
          licenses (db/get-licenses {:wfid workflow-id
                                     :items catalogue-item-ids})]
      (assert (= :workflow/dynamic (:type workflow))
              (str "workflow type was " (:type workflow))) ; TODO: support other workflows
      {:event/type :application.event/created
       :event/time time
       :event/actor actor
       :application/id application-id
       :application/external-id (when allocate-external-id? ;; TODO parameterize id allocation?
                                  (application-external-id! time))
       :application/resources (map (fn [item]
                                     {:catalogue-item/id (:id item)
                                      :resource/ext-id (:resid item)})
                                   items)
       :application/licenses (map (fn [license]
                                    {:license/id (:id license)})
                                  licenses)
       :form/id form-id
       :workflow/id workflow-id
       :workflow/type (:type workflow)})))

(defn add-application-created-event! [opts]
  (add-event! (application-created-event (assoc opts :allocate-external-id? true))))

(defn- get-workflow-id-for-catalogue-items [catalogue-item-ids]
  (:workflow/id (application-created-event {:catalogue-item-ids catalogue-item-ids})))

(defn create-application! [user-id catalogue-item-ids]
  (let [start (time/now)
        app-id (:id (db/create-application! {:user user-id
                                             ;; TODO: remove catalogue_item_application.wfid
                                             :wfid (get-workflow-id-for-catalogue-items catalogue-item-ids)
                                             :start start}))]
    (add-application-created-event! {:application-id app-id
                                     :catalogue-item-ids catalogue-item-ids
                                     :time start
                                     :actor user-id})
    {:success true
     :application-id app-id}))

(defn- valid-user? [userid]
  (not (nil? (users/get-user-attributes userid))))

(defn get-form [form-id]
  (-> (form/get-form form-id)
      (select-keys [:id :organization :title :start :end])
      (assoc :items (->> (db/get-form-items {:id form-id})
                         (mapv #(process-field nil form-id %))))))

(defn- validate-form-answers [form-id answers]
  (let [form (get-form form-id)
        _ (assert form)
        fields (for [field (:items form)]
                 (assoc field :value (get-in answers [:items (:id field)])))]
    (form-validation/validate-fields fields)))

(def ^:private db-injections
  {:valid-user? valid-user?
   :validate-form-answers validate-form-answers
   :secure-token secure-token
   :get-catalogue-item catalogue/get-localized-catalogue-item})

(defn command! [cmd]
  (assert (:application-id cmd))
  ;; Use locks to prevent multiple commands being executed in parallel.
  ;; Serializable isolation level will already avoid anomalies, but produces
  ;; lots of transaction conflicts when there is contention. This lock
  ;; roughly doubles the throughput for rems.db.test-transactions tests.
  (jdbc/execute! db/*db* ["LOCK TABLE application_event IN SHARE ROW EXCLUSIVE MODE"])
  (let [events (get-application-events (:application-id cmd))
        app (-> nil
                (dynamic/apply-events events)
                (model/enrich-workflow-handlers workflow/get-workflow))
        result (dynamic/handle-command cmd app db-injections)]
    (if (:success result)
      (add-event! (:result result))
      result)))

(defn is-dynamic-handler? [user-id application]
  (contains? (set (get-in application [:workflow :handlers])) user-id))

;; TODO use also in UI side?
(defn is-dynamic-application? [application]
  (= :workflow/dynamic (get-in application [:workflow :type])))

(defn accept-invitation [user-id invitation-token]
  (or (when-let [application-id (:id (db/get-application-by-invitation-token {:token invitation-token}))]
        (let [response (command! {:type :application.command/accept-invitation
                                  :actor user-id
                                  :application-id application-id
                                  :token invitation-token
                                  :time (time/now)})]
          (if-not response
            {:success true
             :application-id application-id}
            {:success false
             :errors (:errors response)})))
      {:success false
       :errors [{:type :t.actions.errors/invalid-token :token invitation-token}]}))
