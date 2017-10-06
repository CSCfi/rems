(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [clojure.set :refer [difference
                                 union]]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.catalogue :refer [get-catalogue-item-title
                                       get-localized-catalogue-item]]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.workflow-actors :as actors]
            [rems.email :as email]
            [rems.util :refer [get-user-id
                               get-username
                               index-by]]))

;; TODO cache application state in db instead of always computing it from events
(declare get-application-state)

(defn- not-empty? [args]
  ((complement empty?) args))

;;; Query functions

(defn handling-event? [app e]
  (or (contains? #{"approve" "autoapprove" "reject" "return" "review"} (:event e)) ;; definitely not by applicant
      (and (= "close" (:event e)) (not= (:applicantuserid app) (:userid e))) ;; not by applicant
      ))

(defn handled? [app]
  (or (contains? #{"approved" "rejected" "returned"} (:state app)) ;; by approver action
      (and (contains? #{"closed" "withdrawn"} (:state app))
           (some (partial handling-event? app) (:events app)))))

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

(defn reviewed?
  "Returns true if the application, given as parameter, has already been reviewed normally or as a 3rd party actor by the current user.
   Otherwise, current hasn't yet provided feedback and false is returned."
  ([app]
   (reviewed? app context/*user*))
  ([app userid]
   (contains? (set (map :userid (concat (get-review-events app) (get-third-party-review-events app))))
              (get-user-id userid)))
  ([app userid round]
   (reviewed? (update app :events (fn [events] (filter #(= round (:round %)) events))) userid)))

(defn- can-act-as? [application-id role]
  (let [state (get-application-state application-id)
        round (:curround state)]
    (and (= "applied" (:state state))
         (contains? (set (actors/get-by-role application-id round role))
                    (get-user-id)))))

(defn- round-has-approvers? [application-id round]
  (not-empty? (actors/get-by-role application-id round "approver")))

(defn- is-actor? [application-id role]
  (contains? (set (actors/get-by-role application-id role))
             (get-user-id)))

(defn can-approve? [application-id]
  (can-act-as? application-id "approver"))

(defn- is-approver? [application-id]
  (is-actor? application-id "approver"))

(defn can-review? [application-id]
  (can-act-as? application-id "reviewer"))

(defn- is-reviewer? [application-id]
  (is-actor? application-id "reviewer"))

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

(defn can-third-party-review?
  "Checks if the current user can perform a 3rd party review action on the current round for the given application."
  [application]
  (let [state (get-application-state application)]
    (and (= "applied" (:state state))
         (is-third-party-reviewer? (get-user-id) (:curround state) state))))

(defn get-third-party-reviewers
  "Takes as an argument a structure containing application information and a workflow round. Then returns userids for all users that have been requested to review for the given round."
  [application round]
  (set (map :userid (get-events-of-type application round "review-request"))))

(defn is-applicant? [application]
  (= (:applicantuserid application) (get-user-id)))

(defn may-see-application? [application]
  (let [application-id (:id application)]
    (or (is-applicant? application)
        (is-approver? application-id)
        (is-reviewer? application-id)
        (is-third-party-reviewer? application))))

(defn- get-applications-impl [query-params]
  (doall
   (for [app (db/get-applications query-params)]
     (assoc (get-application-state (:id app))
            :catalogue-item
            (get-in (get-localized-catalogue-item {:id (:catid app)})
                    [:localizations context/*lang*])))))


(defn get-applications []
  (get-applications-impl {:applicant (get-user-id)}))

(defn get-approvals []
  (filterv
   (fn [app] (can-approve? (:id app)))
   (get-applications-impl {})))

(defn get-handled-approvals []
  (->> (get-applications-impl {})
       (filterv (fn [app] (is-approver? (:id app))))
       (filterv handled?)
       (mapv (fn [app]
               (let [my-events (filter #(= (get-user-id) (:userid %))
                                       (:events app))]
                 (assoc app :handled (:time (last my-events))))))))

; TODO: consider refactoring to finding the review events from the current user and mapping those to applications
(defn get-handled-reviews []
  (->> (get-applications-impl {})
       (filterv (fn [app] (or (is-reviewer? (:id app))
                              (is-third-party-reviewer? (get-user-id) app))))
       (filterv reviewed?)
       (mapv (fn [app]
               (let [my-events (filter #(= (get-user-id) (:userid %))
                                       (:events app))]
                 (assoc app :handled (:time (last my-events))))))))

;; TODO notify actors also during other events such as reject, return etc.
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
  (assoc app :review (if (is-reviewer? (:id app)) :normal :third-party)))

(defn get-applications-to-review
  "Returns applications that are waiting for a normal or 3rd party review. Type of the review, with key :review and values :normal or :third-party,
  are added to each application's attributes"
  []
  (->> (get-applications-impl {})
       (filterv
         (fn [app] (and (not (reviewed? app))
                        (or (can-review? (:id app))
                            (can-third-party-review? (:id app))))))
       (mapv assoc-review-type-to-app)))

(defn get-draft-id-for
  "Finds applications in the draft state for the given catalogue item.
   Returns an id of an arbitrary one of them, or nil if there are none."
  [catalogue-item]
  (->> (get-applications-impl {:resource catalogue-item :applicant (get-user-id)})
       (filter #(= "draft" (:state %)))
       first
       :id))

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
   :title (:title item)
   :inputprompt (:inputprompt item)
   :optional (:formitemoptional item)
   :type (:type item)
   :value (when application-id
            (:value
             (db/get-field-value {:item (:id item)
                                  :form form-id
                                  :application application-id})))})

(defn- process-license
  "Returns a license structure like this:

    {:id 2
     :type \"license\"
     :licensetype \"link\"
     :title \"LGPL\"
     :textcontent \"www.license.link\"
     :approved false}"
  [application localizations license]
  (let [app-id (:id application)
        app-user (:applicantuserid application)
        license-id (:id license)
        localized-title (get-in localizations [license-id context/*lang* :title])
        localized-content (get-in localizations [license-id context/*lang* :textcontent])]
    {:id (:id license)
     :type "license"
     :licensetype (:type license)
     :title (or localized-title (:title license))
     :textcontent (or localized-content (:textcontent license))
     :approved (= "approved"
                  (:state
                   (when application
                     (db/get-application-license-approval {:catappid app-id
                                                           :licid (:id license)
                                                           :actoruserid app-user}))))}))

(defn get-form-for
  "Returns a form structure like this:

    {:id 7
     :title \"Title\"
     :application {:id 3
                   :state \"draft\"}
     :applicant-attributes {\"eppn\" \"developer\"
                            \"email\" \"developer@e.mail\"
                            \"displayName\" \"deve\"
                            \"surname\" \"loper\"
                            ...}
     :catalogue-item 3
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
                 :approved false}]"
  ([catalogue-item]
   (get-form-for catalogue-item nil))
  ([catalogue-item application-id]
   (let [form (db/get-form-for-catalogue-item
               {:id catalogue-item :lang (name context/*lang*)})
         application (when application-id
                       (get-application-state application-id))
         form-id (:formid form)
         items (mapv #(process-item application-id form-id %)
                     (db/get-form-items {:id form-id}))
         license-localizations (->> (db/get-license-localizations)
                                    (map #(update-in % [:langcode] keyword))
                                    (index-by [:licid :langcode]))
         licenses (mapv #(process-license application license-localizations %)
                        (db/get-workflow-licenses {:catId catalogue-item}))]
     (when application-id
       (when-not (may-see-application? application)
         (throw-unauthorized)))
     {:id form-id
      :catalogue-item catalogue-item
      :application application
      :applicant-attributes (users/get-user-attributes (:applicantuserid application))
      :title (get-catalogue-item-title
              (get-localized-catalogue-item {:id catalogue-item}))
      :items items
      :licenses licenses})))

(defn create-new-draft [resource-id]
  (let [uid (get-user-id)
        id (:id (db/create-application!
                 {:item resource-id :user uid}))]
    id))

;;; Applying events

(defmulti ^:private apply-event
  "Applies an event to an application state."
  ;; dispatch by event type
  (fn [_application event] (:event event)))

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


;;; Application phases

;; TODO should only be able to see the phase if applicant, approver, reviewer etc.
(defn get-application-phases [state]
  (cond (= state "rejected")
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
         {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]

        (= state "approved")
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :approved? true :text :t.phases/approve}
         {:phase :result :completed? true :approved? true :text :t.phases/approved}]

        (= state "closed")
        [{:phase :apply :closed? true :text :t.phases/apply}
         {:phase :approve :closed? true :text :t.phases/approve}
         {:phase :result :closed? true :text :t.phases/approved}]

        (contains? #{"draft" "returned" "withdrawn"} state)
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        (= "applied" state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :active? true :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        :else
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        ))

;;; Public event api

(defn get-application-state [application-id]
  (let [events (db/get-application-events {:application application-id})
        application (-> {:id application-id}
                        db/get-applications
                        first
                        (assoc :state "draft" :curround 0) ;; reset state
                        (assoc :events events))]
    (apply-events
     application
     events)))

(declare handle-state-change)

(defn try-autoapprove-application
  "If application can be autoapproved (round has no approvers), add an
   autoapprove event. Otherwise do nothing."
  [application]
  (let [application-id (:id application)
        round (:curround application)
        state (:state application)]
    (when (= "applied" state)
      (let [approvers (actors/get-by-role application-id round "approver")
            reviewers (actors/get-by-role application-id round "reviewer")]
        (when (and (empty? approvers)
                   (empty? reviewers))
          (db/add-application-event! {:application application-id :user (get-user-id)
                                      :round round :event "autoapprove" :comment nil})
          true)))))

(defn- send-emails-for [application]
  (let [applicant-attrs (users/get-user-attributes (:applicantuserid application))
        application-id (:id application)
        item-title (get-catalogue-item-title
                     (get-localized-catalogue-item {:id (:catid application)}))
        round (:curround application)
        state (:state application)]
    (if (= "applied" state)
      (let [approvers (actors/get-by-role application-id round "approver")
            reviewers (actors/get-by-role application-id round "reviewer")
            applicant-name (get-username applicant-attrs)
            item-id (:catid application)]
        (doseq [approver approvers] (let [user-attrs (users/get-user-attributes approver)]
                                      (email/approval-request user-attrs applicant-name application-id item-title item-id)))
        (doseq [reviewer reviewers] (let [user-attrs (users/get-user-attributes reviewer)]
                                      (email/review-request user-attrs applicant-name application-id item-title item-id))))
      (email/status-change-alert applicant-attrs
                                 application-id
                                 item-title
                                 state
                                 (:catid application)))))

(defn- add-entitlements-for [application]
  (when (= "approved" (:state application))
    (db/add-entitlement! {:application (:id application)
                          :user (:applicantuserid application)})))

(defn handle-state-change [application-id]
  (let [application (get-application-state application-id)]
    (send-emails-for application)
    (add-entitlements-for application)
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
    (email/confirm-application-creation (get-catalogue-item-title
                                          (get-localized-catalogue-item {:id (:catid application)}))
                                        (:catid application)
                                        application-id)
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
  (when-not (can-approve? application-id)
    (throw-unauthorized))
  (judge-application application-id "approve" round msg))

(defn reject-application [application-id round msg]
  (when-not (can-approve? application-id)
    (throw-unauthorized))
  (judge-application application-id "reject" round msg))

(defn return-application [application-id round msg]
  (when-not (can-approve? application-id)
    (throw-unauthorized))
  (judge-application application-id "return" round msg))

(defn review-application [application-id round msg]
  (when-not (can-review? application-id)
    (throw-unauthorized))
  (judge-application application-id "review" round msg))

(defn perform-third-party-review [application-id round msg]
  (when-not (can-third-party-review? application-id)
    (throw-unauthorized))
  (let [state (get-application-state application-id)]
    (when-not (= round (:curround state))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user (get-user-id)
                                :round round :event "third-party-review" :comment msg})))

(defn send-review-request [application-id round msg recipients]
  (let [state (get-application-state application-id)]
    (when-not (can-approve? application-id)
      (throw-unauthorized))
    (when-not (= round (:curround state))
      (throw-unauthorized))
    (assert (not-empty? recipients)
            (str "Can't send a review request without recipients."))
    (let [send-to (if (vector? recipients)
                    recipients
                    (vector recipients))]
      (doseq [recipient send-to]
        (when-not (is-third-party-reviewer? recipient (:curround state) state)
          (db/add-application-event! {:application application-id :user recipient
                                      :round round :event "review-request" :comment msg})
          (roles/add-role! recipient :reviewer)
          (email/review-request (users/get-user-attributes recipient)
                                (get-username (users/get-user-attributes (:applicantuserid state)))
                                application-id
                                (get-catalogue-item-title
                                  (get-localized-catalogue-item {:id (:catid state)}))
                                (:catid state)))))))

;; TODO better name
;; TODO consider refactoring together with judge
(defn- unjudge-application
  "Action handling for both approver and applicant."
  [application-id event round msg]
  (let [application (get-application-state application-id)
        applicant? (= (:applicantuserid application) (get-user-id))]
    (when-not (or applicant? (can-approve? application-id))
      (throw-unauthorized))
    (let [state (get-application-state application-id)]
      (when (= event "withdraw")
        (when-not (= (:state state) "applied")
          (throw-unauthorized)))
      (when-not (= round (:curround state))
        (throw-unauthorized))
      (db/add-application-event! {:application application-id :user (get-user-id)
                                  :round round :event event :comment msg})
      (let [application (get-application-state application-id)
            user-attrs (users/get-user-attributes (:applicantuserid application))]
        (email/status-change-alert user-attrs
                                   application-id
                                   (get-catalogue-item-title
                                     (get-localized-catalogue-item {:id (:catid application)}))
                                   (:state application)
                                   (:catid application))))))

(defn withdraw-application [application-id round msg]
  (unjudge-application application-id "withdraw" round msg))

(defn close-application [application-id round msg]
  (unjudge-application application-id "close" round msg))
