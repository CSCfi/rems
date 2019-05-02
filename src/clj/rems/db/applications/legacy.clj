(ns rems.db.applications.legacy
  "Functions for dealing with old round-based applications."
  (:require [clojure.test :refer :all]
            [clj-time.core :as time]
            [rems.application-util :refer [form-fields-editable?]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.catalogue :as catalogue]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.permissions :as permissions]
            [rems.workflow.dynamic :as dynamic]))

(defn- assoc-field-previous-values [application fields]
  (let [previous-values (:items (if (form-fields-editable? application)
                                  (:submitted-form-contents application)
                                  (:previous-submitted-form-contents application)))]
    (for [field fields]
      (assoc field :previous-value (get previous-values (:id field))))))

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
  ([user-id application-id]
   (let [form (db/get-form-for-application {:application application-id})
         _ (assert form)
         application (applications/get-application-state application-id)
         application (if (applications/is-dynamic-application? application)
                       (dynamic/assoc-possible-commands user-id application) ; TODO move even higher?
                       application)
         _ (assert application)
         form-id (:formid form)
         _ (assert form-id)
         catalogue-item-ids (mapv :item (db/get-application-items {:application application-id}))
         catalogue-items (catalogue/get-localized-catalogue-items {:ids catalogue-item-ids})
         items (->> (db/get-form-items {:id form-id})
                    (mapv #(applications/process-field application-id form-id %))
                    (assoc-field-previous-values application))
         description (-> (filter #(= "description" (:type %)) items)
                         first
                         :value)
         licenses (get-application-licenses application catalogue-item-ids)
         review-type (cond
                       (applications/can-review? user-id application) :normal
                       (applications/can-third-party-review? user-id application) :third-party
                       :else nil)]
     (when application-id
       (when-not (applications/may-see-application? user-id application)
         (throw-forbidden)))
     {:id form-id
      :title (:formtitle form)
      :catalogue-items catalogue-items
      :application (-> application
                       (assoc :formid form-id
                              :catalogue-items catalogue-items ;; TODO decide if catalogue-items are part of "form" or "application"
                              :can-approve? (applications/can-approve? user-id application)
                              :can-close? (applications/can-close? user-id application)
                              :can-withdraw? (applications/can-withdraw? user-id application)
                              :can-third-party-review? (applications/can-third-party-review? user-id application)
                              :is-applicant? (applications/is-applicant? user-id application)
                              :review-type review-type
                              :description description)
                       (permissions/cleanup))
      :applicant-attributes (users/get-user-attributes (:applicantuserid application))
      :items items
      :licenses licenses
      :accepted-licenses (get-in application [:form-contents :accepted-licenses])
      :phases (applications/get-application-phases (:state application))})))
