(ns rems.api.applications-v2
  (:require [clojure.tools.logging :as log]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.application.model :as model]
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

(defn get-application
  "Returns the part of application state which the specified user
   is allowed to see. Suitable for returning from public APIs as-is."
  [user-id application-id]
  (let [events (applications/get-dynamic-application-events application-id)]
    (if (empty? events)
      nil ;; will result in a 404
      (or (-> (model/build-application-view events injections)
              (model/apply-user-permissions user-id))
          (throw-forbidden)))))

;;; Listing all applications

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

(mount/defstate all-applications-cache
  :start (atom {:last-processed-event-id 0
                :applications nil}))

(defn get-all-applications [user-id]
  (let [cache @all-applications-cache
        events (applications/get-dynamic-application-events-since (:last-processed-event-id cache))
        applications (reduce all-applications-view (:applications cache) events)
        ;; TODO: for a shared cache it may be necessary to make assoc-injections idempotent and consider cache invalidation
        cached-injections (map-vals memoize injections)]
    (when-let [event-id (:event/id (last events))]
      (when (compare-and-set! all-applications-cache
                              cache
                              {:last-processed-event-id event-id
                               :applications applications})
        (log/info "Updated all-applications-cache from" (:last-processed-event-id cache) "to" event-id)))
    (->> (vals applications)
         (map #(model/apply-user-permissions % user-id))
         (remove nil?)
         (map #(model/enrich-with-injections % cached-injections))
         (map exclude-unnecessary-keys-from-overview))))

(defn- own-application? [application]
  (some #{:applicant
          :member}
        (:application/roles application)))

(defn get-own-applications [user-id]
  (->> (get-all-applications user-id)
       (filter own-application?)))
