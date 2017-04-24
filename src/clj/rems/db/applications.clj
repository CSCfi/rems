(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.approvals :refer [approver?]]
            [rems.db.catalogue :refer [get-localized-catalogue-item]]
            [rems.db.core :as db]
            [rems.util :refer [get-user-id index-by]]))

(declare get-application-state new-approver?)

(defn get-applications []
  (doall
   (for [a (db/get-applications {:applicant (get-user-id)})]
     (assoc (get-application-state (:id a)) ;; TODO: hacky
            :catalogue-item
            (get-in (get-localized-catalogue-item {:id (:catid a)})
                    [:localizations context/*lang*])))))

(defn get-draft-id-for ;; TODO: hacky
  "Finds applications in the draft state for the given catalogue item.
   Returns an id of an arbitrary one of them, or nil if there are none."
  [catalogue-item]
  (->> (get-applications)
       (filter #(and (= catalogue-item (:catid %)) (= "draft" (:state %))))
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

(defn- process-comment [approval]
  (select-keys approval [:round :comment :state]))

(defn get-form-for
  "Returns a form structure like this:

    {:id 7
     :title \"Title\"
     :application {:id 3
                   :state \"draft\"}
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
                 :approved false}]
     :comments [{:round 0 :comment \"blah\" :state \"approved\"}]"
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
         applicant? (= (:applicantuserid application) (get-user-id))
         comments (when application-id
                    (mapv process-comment (db/get-application-approvals
                                           {:application application-id})))]
     (when application-id
       (when-not (or applicant?
                     (new-approver? application-id (:curround application)))
         (throw-unauthorized)))
     {:id form-id
      :catalogue-item catalogue-item
      :application application
      :title (or (:formtitle form) (:metatitle form))
      :items items
      :licenses licenses
      :comments comments})))

(defn create-new-draft [resource-id]
  (let [uid (get-user-id)
        id (:id (db/create-application!
                 {:item resource-id :user uid}))]
    id))

;;; new event-based functions

(defmulti ^:private apply-event
  "Applies an event to an application state."
  ;; dispatch by event type
  (fn [_application event] (:event event)))

(defmethod apply-event "apply"
  [application event]
  (assert (= (:state application) "draft")
          (str "Can't submit application " (pr-str application)))
  (assert (= (:round event) 0)
          (str "Apply event should have round 0" (pr-str event)))
  (assoc application :state "applied" :curround 0))

(defmethod apply-event "approve"
  [application event]
  (assert (= (:state application) "applied")
          (str "Can't approve application " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and approval rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (if (= (:curround application) (:fnlround application))
    (assoc application :state "approved")
    (assoc application :state "applied" :curround (inc (:curround application)))))

(defmethod apply-event "reject"
  [application event]
  (assert (= (:state application) "applied")
          (str "Can't reject application " (pr-str application)))
  (assert (= (:curround application) (:round event))
          (str "Application and rejection rounds don't match: "
               (pr-str application) " vs. " (pr-str event)))
  (assoc application :state "rejected"))

;; TODO: "return" event

;; TODO: auto approver event (when no approvers for round)

(defn- apply-events [application events]
  (reduce apply-event application events))

(defn get-application-state [application-id]
  (let [application (-> {:id application-id}
                        db/get-applications
                        first
                        (assoc :state "draft" :curround 0))] ;; reset state
    (apply-events
     application
     (db/get-application-events {:application application-id}))))

(defn new-submit-application [application-id]
  (let [application (first (db/get-applications {:id application-id}))
        uid (get-user-id)]
    (when-not (= uid (:applicantuserid application))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user uid
                                :round 0 :event "apply" :comment nil})))

(defn- new-approver? [application round]
  (contains? (set (db/get-workflow-approvers {:application application :round round}))
             {:appruserid (get-user-id)}))

(defn- judge-application [application-id event round comment]
  (when-not (new-approver? application-id round)
    (throw-unauthorized))
  (let [state (get-application-state application-id)]
    (when-not (= round (:curround state))
      (throw-unauthorized))
    (when-not (= "applied" (:state state))
      (throw-unauthorized))
    (db/add-application-event! {:application application-id :user (get-user-id)
                                :round round :event event :comment comment})))

(defn new-approve-application [application-id round comment]
  (judge-application application-id "approve" round comment))

(defn new-reject-application [application-id round comment]
  (judge-application application-id "reject" round comment))
