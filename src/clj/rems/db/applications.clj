(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.catalogue :refer [get-catalogue-item-title
                                       get-localized-catalogue-item]]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.db.workflow-actors :as actors]
            [rems.util :refer [get-user-id index-by]]))

;; TODO cache application state in db instead of always computing it from events
(declare get-application-state)

;;; Query functions

(defn handled? [app]
  (contains? #{"approved" "rejected" "returned" "closed"} (:state app)))

(defn reviewed? [app]
  (empty (filter #(= "review" (:event %)) (:events app))))

(defn- can-act-as? [application role]
    (let [state (get-application-state application)
        round (:curround state)]
    (and (= "applied" (:state state))
         (contains? (set (actors/get-by-role application round role))
                    (get-user-id)))))

(defn- is-actor? [application role]
  (contains? (set (actors/get-by-role application role))
             (get-user-id)))

(defn can-approve? [application]
  (can-act-as? application "approver"))

(defn is-approver? [application]
  (is-actor? application "approver"))

(defn can-review? [application]
  (can-act-as? application "reviewer"))

(defn is-reviewer? [application]
  (is-actor? application "reviewer"))

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

(defn get-handled-reviews []
  (->> (get-applications-impl {})
       (filterv (fn [app] (is-reviewer? (:id app))))
       (filterv reviewed?)
       (mapv (fn [app]
               (let [my-events (filter #(= (get-user-id) (:userid %))
                                       (:events app))]
                 (assoc app :handled (:time (last my-events))))))))

(defn get-application-to-review []
  (filterv
    (fn [app] (can-review? (:id app)))
    (get-applications-impl {})))

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
                        (db/get-workflow-licenses {:catId catalogue-item}))
         applicant? (= (:applicantuserid application) (get-user-id))]
     (when application-id
       (when-not (or applicant?
                     (is-approver? application-id)
                     (is-reviewer? application-id))
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
    (assoc application :state "approver")
    (assoc application :state "applied" :curround (inc (:curround application)))))

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

(defn try-autoapprove-application
  "If application can be autoapproved (round has no approvers), add an
   autoapprove event. Otherwise do nothing."
  [application-id]
  (let [application (get-application-state application-id)
        round (:curround application)
        state (:state application)]
    (when (= "applied" state)
      (when (and (empty? (actors/get-by-role application-id round "approver"))
                 (empty? (actors/get-by-role application-id round "reviewer")))
        (db/add-application-event! {:application application-id :user (get-user-id)
                                    :round round :event "autoapprove" :comment nil})
        (try-autoapprove-application application-id)))))

(defn submit-application [application-id]
  (let [application (get-application-state application-id)
        uid (get-user-id)]
    (when-not (= uid (:applicantuserid application))
      (throw-unauthorized))
    (when-not (#{"draft" "returned" "withdrawn"} (:state application))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user uid
                                :round 0 :event "apply" :comment nil})
    (try-autoapprove-application application-id)))

(defn- judge-application [application-id event round msg]
  (let [state (get-application-state application-id)]
    (when-not (= round (:curround state))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user (get-user-id)
                                :round round :event event :comment msg})
    (try-autoapprove-application application-id)))

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
      (when-not (= round (:curround state))
        (throw-unauthorized))
      (db/add-application-event! {:application application-id :user (get-user-id)
                                  :round round :event event :comment msg}))))

(defn withdraw-application [application-id round msg]
  (unjudge-application application-id "withdraw" round msg))

(defn close-application [application-id round msg]
  (unjudge-application application-id "close" round msg))
