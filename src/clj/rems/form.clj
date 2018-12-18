(ns rems.form
  (:require [rems.db.applications :refer [create-new-draft
                                          get-application-state
                                          get-catalogue-items-by-application-id
                                          get-form-for
                                          make-draft-application
                                          submit-application]]
            [rems.db.catalogue :refer [disabled-catalogue-item?]]
            [rems.db.core :as db]
            [rems.InvalidRequestException]
            [rems.text :refer :all]
            [rems.util :refer [getx]]
            [rems.context :as context]))

(defn- title-localizations [item]
  (into {} (for [[lang {title :title}] (:localizations item)
                 :when title]
             [lang title])))

;; TODO: in the validation :text, we always use the english title for
;; items since they don't have a non-localized title like licenses.
;; Should probably get rid of non-localize title for licenses as well?

(defn- validate-item
  [item]
  (when-not (:optional item)
    (when (empty? (:value item))
      {:type :item
       :id (:id item)
       :title (title-localizations item)
       :key :t.form.validation/required
       :text (text-format :t.form.validation/required (get-in item [:localizations :en :title]))})))

(defn- validate-license
  [license]
  (when-not (:approved license)
    {:type :license
     :id (:id license)
     :title (title-localizations license)
     :key :t.form.validation/required
     :text (text-format :t.form.validation/required (:title license))}))

(defn- validate
  "Validates a filled in form from (get-form-for application).

   Returns either :valid or a sequence of validation errors."
  [form]
  (let [messages (vec (concat (filterv identity (mapv validate-item (sort-by :id (:items form))))
                              (filterv identity (mapv validate-license (sort-by :id (:licenses form))))))]
    (if (empty? messages)
      :valid
      messages)))

(defn save-application-items [application-id catalogue-item-ids]
  (assert application-id)
  (assert (empty? (filter nil? catalogue-item-ids)) "nils sent in catalogue-item-ids")
  (assert (not (empty? catalogue-item-ids)))
  (doseq [catalogue-item-id catalogue-item-ids]
    (db/add-application-item! {:application application-id :item catalogue-item-id})))

(defn- save-fields
  [user-id application-id input]
  (let [form (get-form-for user-id application-id)]
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
  (let [form (get-form-for user-id application-id)]
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
  (save-licenses applicant-id  application-id licenses)
  (let [form (get-form-for applicant-id application-id)
        validation (validate form)
        valid? (= :valid validation)
        perform-submit? (and submit? valid?)
        success? (or (not submit?) perform-submit?)]
    (when perform-submit?
      (when (get-in form [:application :workflow :type])
        (throw (rems.InvalidRequestException. (str "Can not submit dynamic application via /save"))))
      (submit-application applicant-id application-id))
    (merge {:valid? valid?
            :success? success?}
           (when-not valid? {:validation validation}))))

(defn- check-for-disabled-items! [items]
  (let [disabled-items (filter disabled-catalogue-item? items)]
    (when (seq disabled-items)
      (throw (rems.InvalidRequestException. (str "Disabled catalogue items " (pr-str disabled-items)))))))

(defn- create-new-draft-for-items [user-id catalogue-item-ids]
  (let [draft (make-draft-application user-id catalogue-item-ids)]
    (check-for-disabled-items! (getx draft :catalogue-items))
    (let [wfid (getx draft :wfid)
          id (create-new-draft user-id wfid)]
      (save-application-items id catalogue-item-ids)
      id)))

(defn api-save [{:keys [application-id catalogue-items items licenses command actor]}]
  (assert (or application-id
              (not (empty? catalogue-items))))
  (assert actor)
  (let [;; if no application-id given, create a new application
        application-id (or application-id
                           (create-new-draft-for-items actor catalogue-items))
        _ (check-for-disabled-items! (get-catalogue-items-by-application-id application-id))
        submit? (= command "submit")
        {:keys [success? valid? validation]} (save-form-inputs actor application-id submit? items licenses)]
    (cond-> {:success success?
             :valid valid?}
      (not valid?) (assoc :validation validation)
      success? (assoc :id application-id
                      :state (:state (get-application-state application-id))))))

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
