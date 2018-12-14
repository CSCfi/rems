(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [clojure.set :refer [difference
                                 union]]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.catalogue :refer [get-localized-catalogue-items]]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.licenses :as licenses]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.workflow-actors :as actors]
            [rems.email :as email]
            [rems.util :refer [get-user-id
                               get-username
                               getx
                               getx-user-id
                               update-present]]
            [rems.workflow.dynamic :as dynamic]
            [clj-time.coerce :as time-coerce])
  (:import [java.io ByteArrayOutputStream FileInputStream]))

(defn draft?
  "Is the given `application-id` for an unsaved draft application?"
  [application-id]
  (nil? application-id))

;; TODO cache application state in db instead of always computing it from events
(declare get-application-state)

(defn- not-empty? [args]
  ((complement empty?) args))

;;; Query functions

(defn handling-event? [app e]
  (or (contains? #{"approve" "autoapprove" "reject" "return" "review"
                   :rems.workflow.dynamic/approved :rems.workflow.dynamic/rejected :rems.workflow.dynamic/returned} (:event e)) ;; definitely not by applicant
      (and (= :event/closed (:event e)) (not= (:applicantuserid app) (:actor e))) ;; not by applicant
      (and (= "close" (:event e)) (not= (:applicantuserid app) (:userid e))))) ;; not by applicant

(defn handled? [app]
  (or (contains? #{"approved" "rejected" "returned"
                   :rems.workflow.dynamic/returned :rems.workflow.dynamic/approved :rems.workflow.dynamic/rejected} (:state app)) ;; by approver action
      (and (contains? #{"closed" "withdrawn"
                        :rems.workflow.dynamic/closed} (:state app))
           (some (partial handling-event? app) (concat (:events app) (:dynamic-events app))))))

(defn- get-events-of-type
  "Returns all events of a given type that have occured in an application. Optionally a round parameter can be provided to focus on events occuring during a given round."
  ([app event]
   (filter #(= event (:event %)) (:events app)))
  ([app round event]
   (filter #(and (= event (:event %)) (= round (:round %))) (:events app))))

(defn get-approval-events
  "Returns all approve events within a specific round of an application."
  [app round]
  (get-events-of-type app round "approve"))

(defn get-review-events
  "Returns all review events that have occured in an application. Optionally a round parameter can be provided to focus on reviews occuring during a given round."
  ([app]
   (get-events-of-type app "review"))
  ([app round]
   (get-events-of-type app round "review")))

(defn get-third-party-review-events
  "Returns all third-party-review events that have occured in an application. Optionally a round parameter can be provided to focus on third-party-reviews occuring during a given round."
  ([app]
   (get-events-of-type app "third-party-review"))
  ([app round]
   (get-events-of-type app round "third-party-review")))

(declare is-commenter?)
(declare can-comment?)
(declare is-decider?)
(declare can-decide?)
(declare is-dynamic-application?)

(defn reviewed?
  "Returns true if the application, given as parameter, has already been reviewed normally or as a 3rd party actor by the current user.
   Otherwise, current hasn't yet provided feedback and false is returned."
  ([app]
   (reviewed? app context/*user*))
  ([app user]
   (if (is-dynamic-application? app)
     (let [app-state (get-application-state (:id app))]
       (or (and (is-commenter? (get-user-id user) app-state)
                (not (can-comment? (get-user-id user) (:id app))))
           (and (is-decider? (get-user-id user) app-state)
                (not (can-decide? (get-user-id user) (:id app))))))
     (contains? (set (map :userid (concat (get-review-events app) (get-third-party-review-events app))))
                (get-user-id user))))
  ([app user round]
   (reviewed? (update app :events (fn [events] (filter #(= round (:round %)) events))) user)))

(comment
  (reviewed? (get-application-state 23) "bob"))

(declare fix-workflow-from-db)
(declare is-dynamic-handler?)

(defn can-act-as?
  [application role]
  (or (and (= "applied" (:state application))
           (contains? (set (actors/get-by-role (:id application) (:curround application) role))
                      (getx-user-id)))
      (and (= "approver" role)
           (contains? (dynamic/possible-commands (getx-user-id) (get-application-state (:id application)))
                      :rems.workflow.dynamic/approve))))

(defn- is-actor? [actors]
  (contains? (set actors)
             (get-user-id)))

(declare get-application-state)

(defn- has-actor-role? [application-id role]
  (or (is-actor? (actors/get-by-role application-id role))
      (is-dynamic-handler? (get-application-state application-id) (getx-user-id))))

(defn- can-approve? [application]
  (can-act-as? application "approver"))

(defn- is-approver? [application-id]
  (has-actor-role? application-id "approver"))

(defn- can-review? [application]
  (can-act-as? application "reviewer"))

(defn- is-reviewer? [application-id]
  (has-actor-role? application-id "reviewer"))

(defn- is-third-party-reviewer?
  "Checks if a given user has been requested to review the given application. If no user is provided, the function checks review requests for the current user.
   Additionally a specific round can be provided to narrow the check to apply only to the given round."
  ([application]
   (is-third-party-reviewer? (get-user-id) application))
  ([user application]
   (->> (:events application)
        (filter #(and (= "review-request" (:event %)) (= user (:userid %))))
        (not-empty?)))
  ([user round application]
   (is-third-party-reviewer? user (update application :events (fn [events] (filter #(= round (:round %)) events))))))

(defn- can-third-party-review?
  "Checks if the current user can perform a 3rd party review action on the current round for the given application."
  [application]
  (and (= "applied" (:state application))
       (is-third-party-reviewer? (get-user-id) (:curround application) application)))

;; TODO add to tests
(defn- is-commenter?
  "Checks if a given user has been requested to comment the given application."
  ([user application]
   ;; TODO calculate in backend?
   (->> (:dynamic-events application)
        (mapcat :commenters)
        (some #{user}))))

(defn- can-comment?
  "Checks if the current user can perform a comment action for the given application."
  [user-id application-id]
  (let [application (dynamic/assoc-possible-commands user-id (get-application-state application-id))]
    (contains? (get application :possible-commands) :rems.workflow.dynamic/comment)))

;; TODO add to tests
(defn- is-decider?
  "Checks if a given user has been requested to decide on the given application."
  ([user application]
   ;; TODO calculate in backend?
   (->> (:dynamic-events application)
        (map :decider)
        (some #{user}))))

(defn- can-decide?
  "Checks if the current user can perform a decide action for the given application."
  [user-id application-id]
  (let [application (dynamic/assoc-possible-commands user-id (get-application-state application-id))]
    (contains? (get application :possible-commands) :rems.workflow.dynamic/decide)))

(defn get-approvers [application]
  (actors/get-by-role (:id application) "approver"))

(defn get-reviewers [application]
  (actors/get-by-role (:id application) "reviewer"))

(defn get-third-party-reviewers
  "Takes as an argument a structure containing application information and a optionally the workflow round. Then returns userids for all users that have been requested to review for the given round or all rounds if not given."
  ([application]
   (set (map :userid (get-events-of-type application "review-request"))))
  ([application round]
   (set (map :userid (get-events-of-type application round "review-request")))))

(defn get-handlers [application]
  (let [approvers (get-approvers application)
        reviewers (get-reviewers application)
        third-party-reviewers (get-third-party-reviewers application)]
    (union approvers reviewers third-party-reviewers)))

(defn is-applicant? [application]
  (= (:applicantuserid application) (get-user-id)))

(defn may-see-application? [application]
  (let [application-id (:id application)
        user-id (getx-user-id)]
    (or (is-applicant? application)
        (is-approver? application-id)
        (is-reviewer? application-id)
        (is-third-party-reviewer? application)
        (is-dynamic-handler? application user-id)
        (is-commenter? user-id application)
        (is-decider? user-id application))))

(defn- can-close? [application]
  (let [application-id (:id application)]
    (or (and (is-approver? application-id)
             (= "approved" (:state application)))
        (and (is-applicant? application)
             (not= "closed" (:state application))))))

(defn- can-withdraw? [application]
  (and (is-applicant? application)
       (= (:state application) "applied")))

(defn- translate-catalogue-item [item]
  (merge item
         (get-in item [:localizations context/*lang*])))

(defn- get-catalogue-items
  "Function that returns localized catalogue-items for the given application items, `ids`. Prefetched localized catalogue items, `localized-items`,
  can be given as a parameter to avoid excessive database calls."
  ([ids]
   (mapv translate-catalogue-item
         (get-localized-catalogue-items {:items ids})))
  ([ids localized-items]
   (mapv translate-catalogue-item
         (filter #(some #{(:id %)} ids)
                 localized-items))))

(defn get-catalogue-items-by-application-id
  "Given an `app-id`, the function queries for all the items related to that application and calls `get-catalogue-items` to return all the catalogue items
  for the application with localizations."
  [app-id]
  (get-catalogue-items (mapv :item (db/get-application-items {:application app-id}))))

(defn- get-catalogue-items-by-application-items
  "Given `application-items` and `localized-items`, catalogue items with localizations, the function `get-catalogue-items` to map all the application items
  to the catalogue items with localizations."
  [application-items localized-items]
  (when (seq application-items)
    (get-catalogue-items (mapv :item application-items)
                         localized-items)))

(defn- get-applications-impl-batch
  "Prefetches all possibly relevant data from the database and returns all the applications, according to the query parameters, with all the events
  and catalogue items associated with them."
  [query-params]
  (let [events (db/get-all-application-events)
        application-items (db/get-application-items)
        localized-items (get-localized-catalogue-items)]
    (doall
     (for [app (db/get-applications query-params)]
       (let [catalogue-items (get-catalogue-items-by-application-items (filter #(= (:id app) (:application %)) application-items) localized-items)
             app-events (for [e events
                              :when (= (:id app) (:appid e))]
                          ;; :appid needed only for batching
                          (dissoc e :appid))]
         (assoc (get-application-state app app-events)
                :formid (:formid (first catalogue-items))
                :catalogue-items catalogue-items))))))

(comment
  (binding [context/*user* {"eppn" "developer"}]
    (->> (get-applications-impl-batch {})
         (mapv :id))))

(defn get-my-applications []
  (filter
   #(not= (:state %) "closed") ; don't show deleted applications
   (get-applications-impl-batch {:applicant (getx-user-id)})))

(comment
  (->> (get-applications-impl-batch {:applicant "developer"})
       (mapv :id)))

(defn get-approvals []
  (->> (get-applications-impl-batch {})
       (filterv can-approve?)))

(comment
  (binding [context/*user* {"eppn" "developer"}]
    (->> (get-approvals)
         (mapv :id))))

(defn actors-of-dynamic-application [application]
  (map :actor (:dynamic-events application)))

(defn get-handled-approvals []
  (let [actors (db/get-actors-for-applications {:role "approver"})]
    (->> (get-applications-impl-batch {})
         (filterv handled?)
         (filterv (fn [app]
                    (let [application (get-application-state (:id app))]
                      (if (is-dynamic-application? application)
                        (contains? (set (actors-of-dynamic-application application)) (getx-user-id))
                        (is-actor? (actors/filter-by-application-id actors (:id app))))))))))

(comment
  (binding [context/*user* {"eppn" "developer"}]
    (->> (get-handled-approvals)
         (mapv :id))))

;; TODO: consider refactoring to finding the review events from the current user and mapping those to applications
(defn get-handled-reviews []
  (let [actors (db/get-actors-for-applications {:role "reviewer"})]
    (->> (get-applications-impl-batch {})
         (filterv reviewed?)
         (filterv (fn [app]
                    (or (is-actor? (actors/filter-by-application-id actors (:id app)))
                        (is-third-party-reviewer? (get-user-id) app)
                        (is-commenter? (get-user-id) app)
                        (is-decider? (get-user-id) app)))))))

(comment
  (binding [context/*user* {"eppn" "bob"}]
    (get-handled-reviews))
  (binding [context/*user* {"eppn" "carl"}]
    (get-handled-reviews)))

(defn- check-for-unneeded-actions
  "Checks whether the current event will advance into the next workflow round and notifies to all actors, who didn't react, by email that their attention is no longer needed."
  [application-id round event]
  (when (or (= "approve" event)
            (= "review" event))
    (let [application (get-application-state application-id)
          applicant-name (get-username (users/get-user-attributes (:applicantuserid application)))
          approvers (difference (set (actors/get-by-role application-id round "approver"))
                                (set (map :userid (get-approval-events application round))))
          reviewers (difference (set (actors/get-by-role application-id round "reviewer"))
                                (set (map :userid (get-review-events application round))))
          requestees (difference (get-third-party-reviewers application round)
                                 (set (map :userid (get-third-party-review-events application round))))]
      (doseq [user (union approvers reviewers requestees)] (let [user-attrs (users/get-user-attributes user)]
                                                             (email/action-not-needed user-attrs applicant-name application-id))))))

(defn assoc-review-type-to-app [app]
  (assoc app :review-type (if (is-reviewer? (:id app)) :normal :third-party)))

(defn get-applications-to-review
  "Returns applications that are waiting for a normal or 3rd party review. Type of the review, with key :review and values :normal or :third-party,
  are added to each application's attributes"
  []
  (->> (get-applications-impl-batch {})
       (filterv
        (fn [app] (and (not (reviewed? app))
                       (or (can-review? app)
                           (can-third-party-review? app)
                           (can-comment? (getx-user-id) (:id app))
                           (can-decide? (getx-user-id) (:id app))))))
       (mapv assoc-review-type-to-app)))

(defn check-review-timeout
  "Checks for and times out reviews that are past the associated end time."
  [t]
  (let [reviews (get-applications-to-review)])) ;; TODO implement review timeout later

(defn make-draft-application
  "Make a draft application with an initial set of catalogue items."
  [catalogue-item-ids]
  (let [items (get-catalogue-items catalogue-item-ids)]
    (assert (= 1 (count (distinct (mapv :wfid items)))))
    (assert (= 1 (count (distinct (mapv :formid items)))))
    {:id nil
     :state "draft"
     :applicantuserid (get-user-id)
     :wfid (:wfid (first items))
     :formid (:formid (first items))
     :catalogue-items items
     :events []}))

(defn- get-item-value [item form-id application-id]
  (let [query-params {:item (:id item)
                      :form form-id
                      :application application-id}]
    (if (= "attachment" (:type item))
      (:filename (db/get-attachment query-params))
      (:value (db/get-field-value query-params)))))

(defn- process-item
  "Returns an item structure like this:

    {:id 123
     :type \"texta\"
     :title \"Item title\"
     :inputprompt \"hello\"
     :optional true
     :value \"filled value or nil\"}"
  [application-id form-id item]
  {:id (:id item)
   :optional (:formitemoptional item)
   :type (:type item)
   ;; TODO here we do a db call per item, for licenses we do one huge
   ;; db call. Not sure which is better?
   :localizations (into {} (for [{:keys [langcode title inputprompt]}
                                 (db/get-form-item-localizations {:item (:id item)})]
                             [(keyword langcode) {:title title :inputprompt inputprompt}]))
   :value (or
           (when-not (draft? application-id)
             (get-item-value item form-id application-id))
           "")})

(defn- process-license
  [application license]
  (let [app-id (:id application)
        app-user (:applicantuserid application)
        license-id (:id license)]
    (-> license
        (assoc :type "license"
               :approved (= "approved"
                            (:state
                             (when application
                               (db/get-application-license-approval {:catappid app-id
                                                                     :licid license-id
                                                                     :actoruserid app-user}))))))))

(defn- get-application-licenses [application catalogue-item-ids]
  (mapv #(process-license application %)
        (licenses/get-active-licenses
         (or (:start application) (time/now))
         {:wfid (:wfid application) :items catalogue-item-ids})))

;;; Application phases

(defn get-application-phases [state]
  (cond (contains? #{"rejected" :rems.workflow.dynamic/rejected} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
         {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]

        (contains? #{"approved" :rems.workflow.dynamic/approved} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :approved? true :text :t.phases/approve}
         {:phase :result :completed? true :approved? true :text :t.phases/approved}]

        (contains? #{"closed" :rems.workflow.dynamic/closed} state)
        [{:phase :apply :closed? true :text :t.phases/apply}
         {:phase :approve :closed? true :text :t.phases/approve}
         {:phase :result :closed? true :text :t.phases/approved}]

        (contains? #{"draft" "returned" "withdrawn" :rems.workflow.dynamic/draft} state)
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        (contains? #{"applied" :rems.workflow.dynamic/submitted} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :active? true :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        :else
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]))

(defn get-form-for
  "Returns a form structure like this:

    {:id 7
     :title \"Title\"
     :application {:id 3
                   :state \"draft\"
                   :review-type :normal
                   :can-approve? false
                   :can-close? true
                   :can-withdraw? false
                   :can-third-party-review? false
                   :is-applicant? true
                   :workflow {...}
                   :possible-actions #{...}}
     :applicant-attributes {\"eppn\" \"developer\"
                            \"email\" \"developer@e.mail\"
                            \"displayName\" \"deve\"
                            \"surname\" \"loper\"
                            ...}
     :catalogue-items [{:application 3 :item 123}]
     :items [{:id 123
              :type \"texta\"
              :title \"Item title\"
              :inputprompt \"hello\"
              :optional true
              :value \"filled value or nil\"}
             ...]
     :licenses [{:id 2
                 :type \"license\"
                 :licensetype \"link\"
                 :title \"LGPL\"
                 :textcontent \"http://foo\"
                 :localizations {\"fi\" {:title \"...\" :textcontent \"...\"}}
                 :approved false}]
     :phases [{:phase :apply :active? true :text :t.phases/apply}
              {:phase :approve :text :t.phases/approve}
              {:phase :result :text :t.phases/approved}]}"
  ([application-id]
   (let [form (db/get-form-for-application {:application application-id})
         _ (assert form)
         application (get-application-state application-id)
         application (if (is-dynamic-application? application)
                       (dynamic/assoc-possible-commands (getx-user-id) application) ; TODO move even higher?
                       application)
         _ (assert application)
         form-id (:formid form)
         _ (assert form-id)
         catalogue-item-ids (mapv :item (db/get-application-items {:application application-id}))
         catalogue-items (get-catalogue-items catalogue-item-ids)
         items (mapv #(process-item application-id form-id %)
                     (db/get-form-items {:id form-id}))
         description (-> (filter #(= "description" (:type %)) items)
                         first
                         :value)
         licenses (get-application-licenses application catalogue-item-ids)
         review-type (cond
                       (can-review? application) :normal
                       (can-third-party-review? application) :third-party
                       :else nil)]
     (when application-id
       (when-not (may-see-application? application)
         (throw-unauthorized)))
     {:id form-id
      :title (:formtitle form)
      :catalogue-items catalogue-items
      :application (assoc application
                          :formid form-id
                          :catalogue-items catalogue-items ;; TODO decide if catalogue-items are part of "form" or "application"
                          :can-approve? (can-approve? application)
                          :can-close? (can-close? application)
                          :can-withdraw? (can-withdraw? application)
                          :can-third-party-review? (can-third-party-review? application)
                          :is-applicant? (is-applicant? application)
                          :review-type review-type
                          :description description)
      :applicant-attributes (users/get-user-attributes (:applicantuserid application))
      :items items
      :licenses licenses
      :phases (get-application-phases (:state application))})))

(defn save-attachment!
  [{:keys [tempfile filename content-type]} application-id item-id]
  (let [form (get-form-for application-id)
        byte-array (with-open [input (FileInputStream. tempfile)
                               buffer (ByteArrayOutputStream.)]
                     (clojure.java.io/copy input buffer)
                     (.toByteArray buffer))]
    (when-not (#{"draft" "returned" "withdrawn"} (:state (:application form)))
      (throw-unauthorized))
    (db/save-attachment! {:application application-id
                          :form (:id form)
                          :item item-id
                          :user (get-user-id)
                          :filename filename
                          :type content-type
                          :data byte-array})))

(defn get-draft-form-for
  "Returns a draft form structure like `get-form-for` used when a new application is created."
  ([application]
   (let [application-id (:id application)
         catalogue-item-ids (map :id (:catalogue-items application))
         item-id (first catalogue-item-ids)
         form (db/get-form-for-item {:item item-id})
         form-id (:formid form)
         catalogue-items (:catalogue-items application)
         items (mapv #(process-item application-id form-id %)
                     (db/get-form-items {:id form-id}))
         licenses (get-application-licenses application catalogue-item-ids)]
     {:id application-id
      :title (:formtitle form)
      :catalogue-items catalogue-items
      :application (assoc application
                          :can-approve? false
                          :can-close? false
                          :is-applicant? true
                          :review-type nil)
      :applicant-attributes (users/get-user-attributes (:applicantuserid application))
      :items items
      :licenses licenses
      :phases (get-application-phases (:state application))})))

(defn create-new-draft [wfid]
  (let [uid (get-user-id)
        id (:id (db/create-application! {:user uid :wfid wfid}))]
    id))

(defn create-new-draft-at-time [wfid time]
  (let [uid (get-user-id)
        id (:id (db/create-application! {:user uid :wfid wfid :start time}))]
    id))

;;; Applying events

(defmulti ^:private apply-event
  "Applies an event to an application state."
  ;; dispatch by event type
  (fn [_application event] (:event event)))

(defn get-event-types
  "Fetch sequence of supported event names."
  []
  (keys (methods apply-event)))

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

(defmethod apply-event "add-member"
  [application event]
  (let [data (cheshire/parse-string (:eventdata event))
        uid (getx data "uid")]
    (update application :members #((fnil conj []) % uid))))

(defn- apply-events [application events]
  (reduce apply-event application events))

;;; Public event api

(declare get-dynamic-application-state)

(defn get-application-state
  ([application-id]
   (get-application-state (first (db/get-applications {:id application-id}))
                          (db/get-application-events {:application application-id})))
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

(comment
  (get-application-state 12))

(declare handle-state-change)

(defn try-autoapprove-application
  "If application can be autoapproved (round has no approvers), add an
   autoapprove event. Otherwise do nothing."
  [application]
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
          (db/add-application-event! {:application application-id :user (get-user-id)
                                      :round round :event "autoapprove" :comment nil})
          true)))))

(defn- send-emails-for [application]
  (let [applicant-attrs (users/get-user-attributes (:applicantuserid application))
        application-id (:id application)
        items (get-catalogue-items-by-application-id application-id)
        round (:curround application)
        state (:state application)]
    (if (= "applied" state)
      (let [approvers (actors/get-by-role application-id round "approver")
            reviewers (actors/get-by-role application-id round "reviewer")
            applicant-name (get-username applicant-attrs)]
        (doseq [approver approvers] (let [user-attrs (users/get-user-attributes approver)]
                                      (email/approval-request user-attrs applicant-name application-id items)))
        (doseq [reviewer reviewers] (let [user-attrs (users/get-user-attributes reviewer)]
                                      (email/review-request user-attrs applicant-name application-id items))))
      (email/status-change-alert applicant-attrs
                                 application-id
                                 items
                                 state))))

(defn handle-state-change [application-id]
  (let [application (get-application-state application-id)]
    (send-emails-for application)
    (entitlements/update-entitlements-for application)
    (when (try-autoapprove-application application)
      (recur application-id))))

(defn submit-application [application-id]
  (let [application (get-application-state application-id)
        uid (get-user-id)]
    (when-not (= uid (:applicantuserid application))
      (throw-unauthorized))
    (when-not (#{"draft" "returned" "withdrawn"} (:state application))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user uid
                                :round 0 :event "apply" :comment nil})
    (email/confirm-application-creation application-id (get-catalogue-items-by-application-id application-id))
    (handle-state-change application-id)))

(defn- judge-application [application-id event round msg]
  (let [state (get-application-state application-id)]
    (when-not (= round (:curround state))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user (get-user-id)
                                :round round :event event :comment msg})
    (check-for-unneeded-actions application-id round event)
    (handle-state-change application-id)))

(defn approve-application [application-id round msg]
  (when-not (can-approve? (get-application-state application-id))
    (throw-unauthorized))
  (judge-application application-id "approve" round msg))

(defn reject-application [application-id round msg]
  (when-not (can-approve? (get-application-state application-id))
    (throw-unauthorized))
  (judge-application application-id "reject" round msg))

(defn return-application [application-id round msg]
  (when-not (can-approve? (get-application-state application-id))
    (throw-unauthorized))
  (judge-application application-id "return" round msg))

(defn review-application [application-id round msg]
  (when-not (can-review? (get-application-state application-id))
    (throw-unauthorized))
  (judge-application application-id "review" round msg))

(defn perform-third-party-review [application-id round msg]
  (let [application (get-application-state application-id)]
    (when-not (can-third-party-review? application)
      (throw-unauthorized))
    (when-not (= round (:curround application))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user (get-user-id)
                                :round round :event "third-party-review" :comment msg})))

(defn send-review-request [application-id round msg recipients]
  (let [application (get-application-state application-id)]
    (when-not (can-approve? application)
      (throw-unauthorized))
    (when-not (= round (:curround application))
      (throw-unauthorized))
    (assert (not-empty? recipients)
            (str "Can't send a review request without recipients."))
    (let [send-to (if (vector? recipients)
                    recipients
                    (vector recipients))]
      (doseq [recipient send-to]
        (when-not (is-third-party-reviewer? recipient (:curround application) application)
          (db/add-application-event! {:application application-id :user recipient
                                      :round round :event "review-request" :comment msg})
          (roles/add-role! recipient :reviewer)
          (email/review-request (users/get-user-attributes recipient)
                                (get-username (users/get-user-attributes (:applicantuserid application)))
                                application-id
                                (get-catalogue-items-by-application-id application-id)))))))

;; TODO better name
;; TODO consider refactoring together with judge
(defn- unjudge-application
  "Action handling for both approver and applicant."
  [application event round msg]
  (let [application-id (:id application)]
    (when-not (= round (:curround application))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user (get-user-id)
                                :round round :event event :comment msg})
    (handle-state-change application-id)))

(defn withdraw-application [application-id round msg]
  (let [application (get-application-state application-id)]
    (when-not (can-withdraw? application)
      (throw-unauthorized))
    (unjudge-application application "withdraw" round msg)))

(defn close-application [application-id round msg]
  (let [application (get-application-state application-id)]
    (when-not (can-close? application)
      (throw-unauthorized))
    (unjudge-application application "close" round msg)))

(defn add-member [application-id member]
  (let [application (get-application-state application-id)
        uid (get-user-id)]
    (when-not (= uid (:applicantuserid application))
      (throw-unauthorized))
    (when-not (#{"draft" "returned" "withdrawn"} (:state application))
      (throw-unauthorized))
    (assert (users/get-user-attributes member) (str "User '" member "' must exist"))
    (db/add-application-event! {:application application-id :user uid :round 0
                                :comment nil
                                :event "add-member" :eventdata (cheshire/generate-string {"uid" member})})))

;;; Dynamic workflows


;; TODO could use schemas for these coercions
(defn- fix-workflow-from-db [wf]
  (update (cheshire/parse-string wf keyword)
          :type keyword))

(defn- fix-event-from-db [event]
  (-> event
      :eventdata
      (cheshire/parse-string keyword)
      (update :event keyword)
      (update :time #(when % (time-coerce/from-long (Long/parseLong %))))
      (update-present :decision keyword)))

(defn get-dynamic-application-state [application-id]
  (let [application (first (db/get-applications {:id application-id}))
        events (map fix-event-from-db (db/get-application-events {:application application-id}))
        fixed-application (assoc application
                                 :state ::dynamic/draft
                                 :dynamic-events events
                                 :workflow (fix-workflow-from-db (:workflow application)))]
    (assert (is-dynamic-application? fixed-application))
    (dynamic/apply-events fixed-application events)))

(defn- add-dynamic-event! [event]
  (db/add-application-event! {:application (:application-id event)
                              :user (:actor event)
                              :comment nil
                              :round -1
                              :event (str (:event event))
                              :eventdata (cheshire/generate-string event)})
  nil)

(defn- valid-user? [userid]
  (not (nil? (users/get-user-attributes userid))))

(defn dynamic-command! [cmd]
  (let [app (get-dynamic-application-state (:application-id cmd))
        injections {:valid-user? valid-user?
                    :valid-form-inputs? (constantly true)} ;; TODO implement
        result (dynamic/handle-command cmd app injections)]
    (if (:success result)
      (add-dynamic-event! (:result result))
      result)))

(defn is-dynamic-handler? [application user-id]
  (contains? (set (get-in application [:workflow :handlers])) user-id))

;; TODO use also in UI side?
(defn is-dynamic-application? [application]
  (= :workflow/dynamic (get-in application [:workflow :type])))
