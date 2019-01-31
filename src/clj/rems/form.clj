(ns rems.form
  (:require [clj-time.core :as time]
            [rems.catalogue-util :refer [disabled-catalogue-item?]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.form-validation :as form-validation]
            [rems.InvalidRequestException]
            [rems.util :refer [getx]]))


(defn save-application-items [application-id catalogue-item-ids]
  (assert application-id)
  (assert (empty? (filter nil? catalogue-item-ids)) "nils sent in catalogue-item-ids")
  (assert (not (empty? catalogue-item-ids)))
  (doseq [catalogue-item-id catalogue-item-ids]
    (db/add-application-item! {:application application-id :item catalogue-item-id})))

(defn- save-fields
  [user-id application-id input]
  (let [form (applications/get-form-for user-id application-id)]
    (doseq [{item-id :id :as item} (:items form)]
      (when-let [value (get input item-id)]
        (db/save-field-value! {:application application-id
                               :form (:id form)
                               :item item-id
                               :user user-id
                               :value value})
        (when (= "description" (:type item))
          (db/update-application-description! {:id application-id
                                               :description value}))))))

(defn save-licenses
  [user-id application-id input]
  (let [form (applications/get-form-for user-id application-id)]
    (doseq [{licid :id :as license} (sort-by :id (:licenses form))]
      (if-let [state (get input licid (get input (str "license" licid)))]
        (db/save-license-approval! {:catappid application-id
                                    :round 0
                                    :licid licid
                                    :actoruserid user-id
                                    :state state})
        (db/delete-license-approval! {:catappid application-id
                                      :licid licid
                                      :actoruserid user-id})))))

;; TODO think a better name
(defn- save-form-inputs [applicant-id application-id submit? items licenses]
  (save-fields applicant-id application-id items)
  (save-licenses applicant-id application-id licenses)
  (let [form (applications/get-form-for applicant-id application-id)
        validation (form-validation/validate form)
        valid? (= :valid validation)
        perform-submit? (and submit? valid?)
        success? (or (not submit?) perform-submit?)]
    (when perform-submit?
      (when (get-in form [:application :workflow :type])
        (throw (rems.InvalidRequestException. (str "Can not submit dynamic application via /save"))))
      (applications/submit-application applicant-id application-id))
    (merge {:valid? valid?
            :success? success?}
           (when-not valid? {:validation validation}))))

(defn- check-for-disabled-items! [items]
  (let [disabled-items (filter disabled-catalogue-item? items)]
    (when (seq disabled-items)
      (throw (rems.InvalidRequestException. (str "Disabled catalogue items " (pr-str disabled-items)))))))

(defn- create-new-draft-for-items [user-id catalogue-item-ids]
  (let [draft (applications/make-draft-application user-id catalogue-item-ids)]
    (check-for-disabled-items! (getx draft :catalogue-items))
    (let [wfid (getx draft :wfid)
          id (applications/create-new-draft user-id wfid)]
      (save-application-items id catalogue-item-ids)
      id)))

(defn api-save [{:keys [application-id catalogue-items items licenses command actor]}]
  (assert (or application-id
              (not (empty? catalogue-items))))
  (assert actor)
  (let [;; if no application-id given, create a new application
        application-id (or application-id
                           (create-new-draft-for-items actor catalogue-items))
        _ (check-for-disabled-items! (applications/get-catalogue-items-by-application-id application-id))
        submit? (= command "submit")
        {:keys [success? valid? validation]} (save-form-inputs actor application-id submit? items licenses)
        application (applications/get-application-state application-id)]
    ;; XXX: workaround to make dynamic workflows work with the old API - save to both old and new models
    (if (applications/is-dynamic-application? application)
      (if-let [error (applications/dynamic-command! {:type :rems.workflow.dynamic/save-draft
                                                     :actor actor
                                                     :application-id application-id
                                                     :time (time/now)
                                                     :items items
                                                     :licenses licenses})]
        (throw (RuntimeException. (str "error in save-draft command: " error))))
      (when (= "save" command)
        (db/add-application-event! {:application application-id
                                    :user actor
                                    :round 0
                                    :event "save"
                                    :comment nil})))
    (cond-> {:success success?
             :valid valid?}
      (not valid?) (assoc :validation validation)
      success? (assoc :id application-id
                      :state (:state application)))))

(comment
  (binding [context/*tempura* (fn [& args] (pr-str args))]
    (let [app-id (:id (api-save {:actor "developer"
                                 :command "save"
                                 :catalogue-items [1]
                                 :items {}
                                 :licenses {}}))]
      (api-save {:actor "developer"
                 :application-id app-id
                 :command "submit"
                 :items {1 "x" 2 "y" 3 "z"}
                 :licenses {1 "approved" 2 "approved"}}))))
