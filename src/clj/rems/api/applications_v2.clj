(ns rems.api.applications-v2
  (:require [clojure.core.cache :as cache]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.application.events-cache :as events-cache]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.util :refer [getx atom?]])
  (:import [java.util.concurrent TimeUnit ScheduledThreadPoolExecutor]))

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

;; short-lived cache to speed up pollers which get the application
;; repeatedly for each event instead of building their own projection
(mount/defstate application-cache
  :start (atom (cache/ttl-cache-factory {} :ttl 10000)))

(defn get-unrestricted-application
  "Returns the full application state without any user permission
   checks and filtering of sensitive information. Don't expose via APIs."
  [application-id]
  (let [events (applications/get-dynamic-application-events application-id)
        cache-key [application-id (count events)]
        build-app (fn [_] (model/build-application-view events injections))]
    (if (empty? events)
      nil ; application not found
      (if (atom? application-cache) ; guard against not started cache
        (-> (swap! application-cache cache/through-cache cache-key build-app)
            (getx cache-key))
        (build-app nil)))))

(defn get-application
  "Returns the part of application state which the specified user
   is allowed to see. Suitable for returning from public APIs as-is."
  [user-id application-id]
  (when-let [application (get-unrestricted-application application-id)]
    (or (model/apply-user-permissions application user-id)
        (throw-forbidden))))

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
  :start (events-cache/new))

(defn get-all-unrestricted-applications []
  (->> (events-cache/refresh!
        all-applications-cache
        (fn [state events]
          ;; Because enrich-with-injections is not idempotent,
          ;; it's necessary to hold on to the "raw" applications.
          (let [raw-apps (reduce all-applications-view (::raw-apps state) events)
                updated-app-ids (distinct (map :application/id events))
                cached-injections (map-vals memoize injections)
                enriched-apps (->> (select-keys raw-apps updated-app-ids)
                                   (map-vals #(model/enrich-with-injections % cached-injections))
                                   (merge (::enriched-apps state)))]
            {::raw-apps raw-apps
             ::enriched-apps enriched-apps})))
       ::enriched-apps
       (vals)))

(defn get-all-applications [user-id]
  (->> (get-all-unrestricted-applications)
       (map #(model/apply-user-permissions % user-id))
       (remove nil?)
       (map exclude-unnecessary-keys-from-overview)))

(defn- own-application? [application]
  (some #{:applicant :member} (:application/roles application)))

(defn get-own-applications [user-id]
  (->> (get-all-applications user-id)
       (filter own-application?)))

(defn reload-cache! []
  (events-cache/empty! all-applications-cache)
  (get-all-unrestricted-applications)) ; refill the cache

;; empty the cache occasionally in case some of the injected entities are changed
(mount/defstate all-applications-cache-reloader
  :start (doto (ScheduledThreadPoolExecutor. 1)
           (.scheduleWithFixedDelay reload-cache! 1 1 TimeUnit/HOURS))
  :stop (doto all-applications-cache-reloader
          (.shutdown)
          (.awaitTermination 60 TimeUnit/SECONDS)))
