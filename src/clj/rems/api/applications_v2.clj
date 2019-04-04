(ns rems.api.applications-v2
  (:require [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]))

(defn- get-form [form-id]
  (-> (form/get-form form-id)
      (select-keys [:id :organization :title :start :end])
      (assoc :items (->> (db/get-form-items {:id form-id})
                         (mapv #(applications/process-field nil form-id %))))))

(defn- get-catalogue-item [catalogue-item-id]
  (assert (int? catalogue-item-id)
          (pr-str catalogue-item-id))
  (applications/get-catalogue-item catalogue-item-id))

(defn- get-license [license-id]
  (licenses/get-license license-id))

(defn- get-user [user-id]
  (users/get-user-attributes user-id))

(def ^:private injections {:get-form get-form
                           :get-catalogue-item get-catalogue-item
                           :get-license get-license
                           :get-user get-user})

(defn get-unrestricted-application
  "Returns the full application state without any user permission
   checks and filtering of sensitive information. Don't expose via APIs."
  [application-id]
  (let [events (applications/get-dynamic-application-events application-id)]
    (if (empty? events)
      nil
      (model/build-application-view events injections))))

(defn api-get-application-v2
  "Returns the part of application state which the specified user
   is allowed to see. Suitable for returning from public APIs as-is."
  [user-id application-id]
  (let [events (applications/get-dynamic-application-events application-id)]
    (if (empty? events)
      nil ;; will result in a 404
      (or (-> (model/build-application-view events injections)
              (model/apply-user-permissions user-id))
          (throw-forbidden)))))

;;; v1 API compatibility layer

(defn- assoc-derived-data [user-id application]
  (assoc application
         :can-approve? (applications/can-approve? user-id application)
         :can-close? (applications/can-close? user-id application)
         :can-withdraw? (applications/can-withdraw? user-id application)
         :can-third-party-review? (applications/can-third-party-review? user-id application)
         :is-applicant? (applications/is-applicant? user-id application)))

(defn- transform-v2-to-v1 [application user-id]
  (let [form (:application/form application)
        workflow (:application/workflow application)
        catalogue-items (map (fn [resource]
                               (applications/translate-catalogue-item
                                {:id (:catalogue-item/id resource)
                                 :resource-id (:resource/id resource)
                                 :resid (:resource/ext-id resource)
                                 :wfid (:workflow/id workflow)
                                 :formid (:form/id form)
                                 :start (:catalogue-item/start resource)
                                 :end (:catalogue-item/end resource)
                                 :archived (:catalogue-item/archived resource)
                                 :enabled (:catalogue-item/enabled resource)
                                 :title (:default (:catalogue-item/title resource))
                                 :localizations (into {} (for [lang (-> (set (keys (:catalogue-item/title resource)))
                                                                        (disj :default))]
                                                           [lang {:title (get-in resource [:catalogue-item/title lang])
                                                                  :langcode lang
                                                                  :id (:catalogue-item/id resource)}]))}))
                             (:application/resources application))]
    {:id (:form/id form)
     :title (:form/title form)
     :catalogue-items catalogue-items
     :applicant-attributes (:application/applicant-attributes application)
     :application (assoc-derived-data
                   user-id
                   {:id (:application/id application)
                    :formid (:form/id form)
                    :wfid (:workflow/id workflow)
                    :applicantuserid (:application/applicant application)
                    :members (into [{:userid (:application/applicant application)}]
                                   (:application/members application))
                    :invited-members (vec (:application/invited-members application))
                    :start (:application/created application)
                    :last-modified (:application/last-activity application)
                    :state (:application/state application) ; TODO: round-based workflows
                    :description (:application/description application)
                    :catalogue-items catalogue-items
                    :events [] ; TODO: round-based workflows
                    :dynamic-events (:application/events application)
                    :workflow {:type (:workflow/type workflow)
                               ;; TODO: add :handlers only when it exists? https://stackoverflow.com/a/16375390
                               :handlers (vec (:workflow.dynamic/handlers workflow))}
                    :possible-commands (:application/permissions application)
                    :fnlround 0 ; TODO: round-based workflows
                    :review-type nil}) ; TODO: round-based workflows
     :phases (applications/get-application-phases (:application/state application))
     :licenses (map (fn [license]
                      {:id (:license/id license)
                       :type "license"
                       :licensetype (name (:license/type license))
                       ;; TODO: Licenses have three different start times: license.start, workflow_licenses.start, resource_licenses.start
                       ;;       (also catalogue_item_application_licenses.start but that table looks unused)
                       ;;       The old API returns either workflow_licenses.start or resource_licenses.start,
                       ;;       the new one returns license.start for now. Should we keep all three or simplify?
                       :start (:license/start license)
                       :end (:license/end license)
                       :enabled (:license/enabled license)
                       :archived (:license/archived license)
                       :approved (:license/accepted license)
                       :title (:default (:license/title license))
                       :textcontent (:default (or (:license/link license)
                                                  (:license/text license)
                                                  (:license/attachment-filename license)))
                       :attachment-id (:default (:license/attachment-id license))
                       :localizations (into {} (for [lang (-> (set (concat (keys (:license/title license))
                                                                           (keys (:license/link license))
                                                                           (keys (:license/text license))))
                                                              (disj :default))]
                                                 [lang {:title (get-in license [:license/title lang])
                                                        :textcontent (or (get-in license [:license/link lang])
                                                                         (get-in license [:license/text lang])
                                                                         (get-in license [:license/attachment-filename lang]))
                                                        :attachment-id (get-in license [:license/attachment-id lang])}]))})
                    (:application/licenses application))
     :accepted-licenses (:application/accepted-licenses application)
     :items (map (fn [field]
                   {:id (:field/id field)
                    :type (name (:field/type field))
                    :optional (:field/optional field)
                    :options (:field/options field)
                    :maxlength (:field/max-length field)
                    :value (:field/value field)
                    :previous-value (:field/previous-value field)
                    :localizations (into {} (for [lang (set (concat (keys (:field/title field))
                                                                    (keys (:field/placeholder field))))]
                                              [lang {:title (get-in field [:field/title lang])
                                                     :inputprompt (get-in field [:field/placeholder lang])}]))})
                 (:form/fields form))}))

(defn api-get-application-v1 [user-id application-id]
  (when-let [v2 (api-get-application-v2 user-id application-id)]
    (transform-v2-to-v1 v2 user-id)))

;;; v2 API, listing all applications

(defn- all-applications-view
  "Projection for the current state of all applications."
  [applications event]
  (if-let [app-id (:application/id event)] ; old style events don't have :application/id
    (update applications app-id model/application-view event)
    applications))

(defn- exclude-unnecessary-keys-from-overview [application]
  (dissoc application
          :application/events
          :application/form
          :application/licenses))

(defn get-all-applications-v2 [user-id]
  ;; TODO: cache the applications and build the projection incrementally as new events are published
  (let [events (applications/get-dynamic-application-events-since 0)
        applications (reduce all-applications-view nil events)]
    (->> (vals applications)
         (map #(model/apply-user-permissions % user-id))
         (remove nil?)
         ;; TODO: for caching it may be necessary to make assoc-injections idempotent and consider cache invalidation
         (map #(model/enrich-with-injections % injections))
         (map exclude-unnecessary-keys-from-overview))))

(defn- own-application? [application]
  (some #{:applicant
          :member}
        (:application/roles application)))

(defn get-user-applications-v2 [user-id]
  (->> (get-all-applications-v2 user-id)
       (filter own-application?)))

(defn- review? [application]
  (and (some #{:handler
               :commenter
               :past-commenter
               :decider
               :past-decider}
             (:application/roles application))
       (not= :application.state/draft (:application/state application))))

(defn get-all-reviews-v2 [user-id]
  (->> (get-all-applications-v2 user-id)
       (filter review?)))

(defn- open-review? [application]
  (some #{:application.command/approve
          :application.command/comment
          :application.command/decide}
        (:application/permissions application)))

(defn get-open-reviews-v2 [user-id]
  (->> (get-all-reviews-v2 user-id)
       (filter open-review?)))

(defn get-handled-reviews-v2 [user-id]
  (->> (get-all-reviews-v2 user-id)
       (remove open-review?)))
