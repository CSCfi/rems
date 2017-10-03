(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.catalogue :refer [get-catalogue-item-title
                                       get-localized-catalogue-item]]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.db.workflow-actors :as actors]
            [rems.email :as email]
            [rems.util :refer [get-user-id
                               get-username
                               index-by]]))

;; TODO cache application state in db instead of always computing it from events
(declare get-application-state)

;;; Query functions

(defn handling-event? [app e]
  (or (contains? #{"approve" "autoapprove" "reject" "return" "review"} (:event e)) ;; definitely not by applicant
      (and (= "close" (:event e)) (not= (:applicantuserid app) (:userid e))) ;; not by applicant
      ))

(defn handled? [app]
  (or (contains? #{"approved" "rejected" "returned"} (:state app)) ;; by approver action
      (and (contains? #{"closed" "withdrawn"} (:state app))
           (some (partial handling-event? app) (:events app)))))

(defn reviewed? [app]
  (let [not-empty? (complement empty?)]
    (not-empty? (filter #(= "review" (:event %)) (:events app)))))

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

(defn translate-catalogue-item [item-id]
  (let [item (get-localized-catalogue-item {:id item-id})]
    (merge item
           (get-in item [:localizations context/*lang*]))))

(defn- get-catalogue-items [ids]
  (mapv (comp translate-catalogue-item :id)
        (db/get-catalogue-items {:items ids})))

(defn- get-applications-impl [query-params]
  (doall
   (for [app (db/get-applications query-params)]
     (assoc (get-application-state (:id app))
            :catalogue-items (let [items (db/get-application-items {:application (:id app)})]
                               (when (seq items) (get-catalogue-items (mapv :item items)))
                               )))))

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
  [items]
  (->> nil ;; TODO implement (get-applications-impl {:resource catalogue-item :applicant (get-user-id)})
       (filter #(= "draft" (:state %)))
       first
       :id))

(defn make-draft-application
  "Make a draft application with an initial set of catalogue items."
  [application-id catalogue-item-ids]
  (let [items (get-catalogue-items catalogue-item-ids)]
    (assert (= 1 (count (distinct (mapv :wfid items)))))
    {:id application-id
     :state "draft"
     :wfid (:wfid (first items))
     :catalogue-items items}))

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
   :value (when (pos? application-id)
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
                 :approved false}]"
  ([application-id]
   (let [form (db/get-form-for-application {:application application-id :lang (name context/*lang*)})
         _ (assert form)
         application (get-application-state application-id)
         _ (assert application)
         form-id (:formid form)
         _ (assert form-id)
         catalogue-item-ids (mapv :item (db/get-application-items {:application application-id}))
         catalogue-items (get-catalogue-items catalogue-item-ids)
         items (mapv #(process-item application-id form-id %)
                     (db/get-form-items {:id form-id}))
         license-localizations (->> (db/get-license-localizations)
                                    (map #(update-in % [:langcode] keyword))
                                    (index-by [:licid :langcode]))
         licenses (mapv #(process-license application license-localizations %)
                        (db/get-application-licenses {:id application-id}))
         applicant? (= (:applicantuserid application) (get-user-id))]
     (when-not (or applicant?
                   (is-approver? application-id)
                   (is-reviewer? application-id))
       (throw-unauthorized))
     {:id form-id
      :catalogue-items catalogue-items
      :application application
      :applicant-attributes (users/get-user-attributes (:applicantuserid application))
      :items items
      :licenses licenses})))

(defn get-draft-form-for
  "Returns a draft form structure like `get-form-for` used when a new application is created."
  ([application]
   (let [application-id (:id application)
         catalogue-item-ids (map :id (:catalogue-items application))
         item-id (first catalogue-item-ids)
         form (db/get-form-for-item {:item item-id :lang (name context/*lang*)})
         form-id (:formid form)
         wfid (:wfid application)
         catalogue-items (:catalogue-items application) #_(db/get-application-items {:application application-id})
         items (mapv #(process-item application-id form-id %)
                     (db/get-form-items {:id form-id}))
         license-localizations (->> (db/get-license-localizations)
                                    (map #(update-in % [:langcode] keyword))
                                    (index-by [:licid :langcode]))
         licenses (mapv #(process-license application license-localizations %)
                        (db/get-draft-licenses {:wfid wfid :items catalogue-item-ids}))]
     {:id form-id
      :catalogue-items catalogue-items
      :application application
      :applicant-attributes (users/get-user-attributes (:applicantuserid application))
      :items items
      :licenses licenses})))

(defn create-new-draft [wfid]
  (let [uid (get-user-id)
        id (:id (db/create-application! {:user uid :wfid wfid}))]
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
        fnlround (:fnlround application)
        state (:state application)]
    (when (= "applied" state)
      (let [approvers (actors/get-by-role application-id round "approver")
            reviewers (actors/get-by-role application-id round "reviewer")]
        (when (and (empty? approvers)
                   (empty? reviewers)
                   (< round fnlround))
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

(defn handle-state-change [application-id]
  (let [application (get-application-state application-id)]
    (send-emails-for application)
    (when (try-autoapprove-application application)
      (recur application-id))))

(defn submit-application [application-id]
  (let [application (get-application-state application-id)
        uid (get-user-id)]
    (when-not (= uid (:applicantuserid application))
      (throw-unauthorized))
    (when-not (#{"draft" "returned" "withdrawn"} (:state application))
      (throw-unauthorized))
    (println 1)
    (db/add-application-event! {:application application-id :user uid
                                :round 0 :event "apply" :comment nil})
    (println 2)
    #_(email/confirm-application-creation (get-catalogue-item-title
                                           (get-localized-catalogue-item {:id (:catid application)}))
                                          (:catid application)
                                          application-id)
    (handle-state-change application-id)
    (println 3)))

(defn- judge-application [application-id event round msg]
  (let [state (get-application-state application-id)]
    (when-not (= round (:curround state))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user (get-user-id)
                                :round round :event event :comment msg})
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
