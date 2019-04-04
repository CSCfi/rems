(ns rems.api.applications-v2
  (:require [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
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
  (catalogue/get-localized-catalogue-item catalogue-item-id))

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
