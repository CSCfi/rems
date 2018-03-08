(ns rems.form
  (:require [clojure.set :refer [difference]]
            [clojure.string :as s]
            [compojure.core :refer [GET POST defroutes]]
            [hiccup.util :refer [url]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.applicant-info :as applicant-info]
            [rems.collapsible :as collapsible]
            [rems.context :as context]
            [rems.db.applications :refer [create-new-draft
                                          draft?
                                          get-application-phases
                                          get-application-state
                                          get-draft-form-for
                                          get-form-for
                                          is-applicant?
                                          make-draft-application
                                          submit-application]]
            [rems.db.catalogue :refer [disabled-catalogue-item?]]
            [rems.db.core :as db]
            [rems.events :as events]
            [rems.guide :refer :all]
            [rems.info-field :as info-field]
            [rems.InvalidRequestException]
            [rems.layout :as layout]
            [rems.phase :refer [phases]]
            [rems.roles :refer [has-roles?]]
            [rems.text :refer :all]
            [rems.util :refer [get-user-id getx getx-in]]
            [ring.util.response :refer [redirect]]))


;; TODO not yet implemented for SPA
(defn- may-see-event?
  "May the current user see this event?

  Applicants can't see review events, reviewers and approvers can see everything."
  [event]
  ;; could implement more granular checking based on authors etc.
  ;; now strictly role-based
  (let [applicant-types #{"apply" "autoapprove" "approve" "reject" "return" "withdraw" "close"}]
    (or (has-roles? :reviewer :approver) ;; reviewer and approver can see everything
        (applicant-types (:event event)))))

;; TODO not yet implemented for SPA
(defn- validate-item
  [item]
  (when-not (:optional item)
    (when (empty? (:value item))
      (text-format :t.form.validation/required (:title item)))))

(defn- validate-license
  [license]
  (when-not (:approved license)
    (text-format :t.form.validation/required (:title license))))

(defn- validate
  "Validates a filled in form from (get-form-for application).

   Returns either :valid or a sequence of validation errors."
  [form]
  (let [messages (vec (concat (filterv identity (mapv validate-item (sort-by :id (:items form))))
                              (filterv identity (mapv validate-license (sort-by :id (:licenses form))))))]
    (if (empty? messages)
      :valid
      messages)))

(defn- format-validation-messages
  [msgs]
  [:ul
   (for [m msgs]
     [:li m])])

(defn save-application-items [application-id catalogue-item-ids]
  (assert application-id)
  (assert (empty? (filter nil? catalogue-item-ids)) "nils sent in catalogue-item-ids")
  (assert (not (empty? catalogue-item-ids)))
  (doseq [catalogue-item-id catalogue-item-ids]
    (db/add-application-item! {:application application-id :item catalogue-item-id})))

(defn- save-fields
  [application-id input]
  (let [form (get-form-for application-id)]
    (doseq [{item-id :id :as item} (:items form)]
      (when-let [value (get input item-id)]
        (db/save-field-value! {:application application-id
                               :form (:id form)
                               :item item-id
                               :user (get-user-id)
                               :value value})))))

(defn save-licenses
  [application-id input]
  (let [form (get-form-for application-id)]
    (doseq [{licid :id :as license} (sort-by :id (:licenses form))]
      (if-let [state (get input licid (get input (str "license" licid)))]
        (db/save-license-approval! {:catappid application-id
                                    :round 0
                                    :licid licid
                                    :actoruserid (get-user-id)
                                    :state state})
        (db/delete-license-approval! {:catappid application-id
                                      :licid licid
                                      :actoruserid (get-user-id)})))))

(defn- save-internal [application catalogue-items items licenses]
  (let [application-id (:id application)
        item-ids (mapv :id catalogue-items)
        disabled-items (filter disabled-catalogue-item? catalogue-items)]
    (when (seq disabled-items)
      (throw (rems.InvalidRequestException. (str "Disabled catalogue items " (pr-str disabled-items)))))
    (save-application-items application-id item-ids)
    (save-fields application-id items)
    (save-licenses application-id licenses)
    (let [submit? (get items "submit")
          form (get-form-for application-id)
          validation (validate form)
          valid? (= :valid validation)
          perform-submit? (and submit? valid?)
          success? (or (not submit?) perform-submit?)]
      (when perform-submit?
        (submit-application application-id))
      {:validation validation
       :valid? valid?
       :success? success?})))

(defn api-save [request]
  (let [{:keys [application-id items licenses command]} request
        catalogue-item-ids (:catalogue-items request)
        application (make-draft-application -1 catalogue-item-ids)
        items (if (= command "submit") (assoc items "submit" true) items)
        db-application-id (if (draft? application-id)
                            (create-new-draft (getx application :wfid))
                            application-id)
        application (assoc application :id db-application-id)
        catalogue-items (:catalogue-items application)
        {:keys [success? valid? validation]} (save-internal application catalogue-items items licenses)]
    (cond-> {:success success?
             :valid valid?}
      (not valid?) (assoc :validation validation)
      success? (assoc :id db-application-id
                      :state (:state (get-application-state application-id))))))
