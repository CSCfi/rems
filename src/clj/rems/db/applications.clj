(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [clojure.core.cache.wrapped :as cache]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [conman.core :as conman]
            [medley.core :refer [distinct-by map-vals]]
            [mount.core :as mount]
            [rems.application.events-cache :as events-cache]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.common.util :refer [conj-set keep-keys]]
            [rems.config :refer [env]]
            [rems.db.attachments :as attachments]
            [rems.db.blacklist :as blacklist]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.csv :as csv]
            [rems.db.events :as events]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.permissions :as permissions]
            [rems.scheduler :as scheduler])
  (:import [org.joda.time Duration]))

;;; Creating applications

(defn- allocate-external-id! [prefix]
  (conman/with-transaction [rems.db.core/*db* {:isolation :serializable}]
    ;; avoid conflicts due to serializable isolation; otherwise this transaction would need retry logic
    (jdbc/execute! db/*db* ["LOCK TABLE external_application_id IN SHARE ROW EXCLUSIVE MODE"])
    (let [all (db/get-external-ids {:prefix prefix})
          last (apply max (cons 0 (map (comp read-string :suffix) all)))
          new (str (inc last))]
      (db/add-external-id! {:prefix prefix :suffix new})
      {:prefix prefix :suffix new})))

(defn- format-external-id [{:keys [prefix suffix]}]
  (str prefix "/" suffix))

(defn- application-external-id! [time]
  (let [id-prefix (str (.getYear time))]
    (format-external-id (allocate-external-id! id-prefix))))

(defn allocate-application-ids! [time]
  {:application/id (:id (db/create-application!))
   :application/external-id (application-external-id! time)})

;;; Running commands

(defn get-catalogue-item-licenses [catalogue-item-id]
  (let [item (catalogue/get-localized-catalogue-item catalogue-item-id {})
        workflow-licenses (-> (workflow/get-workflow (:wfid item))
                              (get-in [:workflow :licenses]))]
    (->> (licenses/get-licenses {:items [catalogue-item-id]})
         (keep-keys {:id :license/id})
         (into workflow-licenses)
         (distinct-by :license/id))))

(defn get-application-by-invitation-token [invitation-token]
  (:id (db/get-application-by-invitation-token {:token invitation-token})))

;;; Fetching applications (for API)

(def ^:private form-template-cache (cache/ttl-cache-factory {}))
(def ^:private catalogue-item-cache (cache/ttl-cache-factory {}))
(def ^:private license-cache (cache/ttl-cache-factory {}))
(def ^:private user-cache (cache/ttl-cache-factory {}))
(def ^:private users-with-role-cache (cache/ttl-cache-factory {}))
(def ^:private workflow-cache (cache/ttl-cache-factory {}))
(def ^:private blacklist-cache (cache/ttl-cache-factory {}))

(defn empty-injections-cache! []
  (swap! form-template-cache empty)
  (swap! catalogue-item-cache empty)
  (swap! license-cache empty)
  (swap! user-cache empty)
  (swap! users-with-role-cache empty)
  (swap! workflow-cache empty)
  (swap! blacklist-cache empty))

(def fetcher-injections
  {:get-attachments-for-application attachments/get-attachments-for-application
   :get-form-template #(cache/lookup-or-miss form-template-cache % form/get-form-template)
   :get-catalogue-item #(cache/lookup-or-miss catalogue-item-cache % (fn [id] (catalogue/get-localized-catalogue-item id {:expand-names? true
                                                                                                                          :expand-resource-data? true})))
   :get-config (fn [] env)
   :get-license #(cache/lookup-or-miss license-cache % licenses/get-license)
   :get-user #(cache/lookup-or-miss user-cache % users/get-user)
   :get-users-with-role #(cache/lookup-or-miss users-with-role-cache % users/get-users-with-role)
   :get-workflow #(cache/lookup-or-miss workflow-cache % workflow/get-workflow)
   :blacklisted? #(cache/lookup-or-miss blacklist-cache [%1 %2] (fn [[userid resource]]
                                                                  (blacklist/blacklisted? userid resource)))
   ;; TODO: no caching for these, but they're only used by command handlers currently
   :get-attachment-metadata attachments/get-attachment-metadata
   :get-catalogue-item-licenses get-catalogue-item-licenses})

(defn get-application-internal
  "Returns the full application state without any user permission
   checks and filtering of sensitive information. Don't expose via APIs."
  [application-id]
  (let [events (events/get-application-events application-id)]
    (if (empty? events)
      nil ; application not found
      (model/build-application-view events fetcher-injections))))

(defn get-application
  "Full application state with internal information hidden. Not personalized for any users. Suitable for public APIs."
  [application-id]
  (when-let [application (get-application-internal application-id)]
    (-> application
        (model/hide-non-public-information)
        (model/apply-privacy-by-roles #{:reporter})))) ;; to populate required :field/private attributes

(defn get-application-for-user
  "Returns the part of application state which the specified user
   is allowed to see. Suitable for returning from public APIs as-is."
  [user-id application-id]
  (when-let [application (get-application-internal application-id)]
    (or (model/apply-user-permissions application user-id)
        (throw-forbidden))))

;;; Listing all applications

(defn- all-applications-view
  "Projection for the current state of all applications."
  [applications event]
  (if-let [app-id (:application/id event)] ; old style events don't have :application/id
    (update applications app-id model/application-view event)
    applications))

(defn- ->ApplicationOverview [application]
  (dissoc application
          :application/events
          :application/forms
          :application/licenses))

(mount/defstate
  ^{:doc "The cached state will contain the following keys:
          ::raw-apps
          - Map from application ID to the pure projected state of an application.
          ::enriched-apps
          - Map from application ID to the enriched version of an application.
            Built from the raw apps by calling `enrich-with-injections`.
            Since the injected entities (resources, forms etc.) are mutable,
            it creates a cache invalidation problem here.
          ::apps-by-user
          - Map from user ID to a list of applications which the user can see.
            Built from the enriched apps by calling `apply-user-permissions`.
          ::roles-by-user
          - Map from user ID to a set of all application roles which the user has,
            a union of roles from all applications."}
  all-applications-cache
  :start (events-cache/new))

(defn- group-apps-by-user [apps]
  (->> apps
       (mapcat (fn [app]
                 (for [user (keys (:application/user-roles app))]
                   (when-let [app (model/apply-user-permissions app user)]
                     [user app]))))
       (reduce (fn [apps-by-user [user app]]
                 (update apps-by-user user conj app))
               {})))

(deftest test-group-apps-by-user
  (let [apps [(-> {:application/id 1}
                  (permissions/give-role-to-users :foo ["user-1" "user-2"]))
              (-> {:application/id 2}
                  (permissions/give-role-to-users :bar ["user-1"]))]]
    (is (= {"user-1" [{:application/id 1} {:application/id 2}]
            "user-2" [{:application/id 1}]}
           (->> (group-apps-by-user apps)
                (map-vals (fn [apps]
                            (->> apps
                                 (map #(select-keys % [:application/id]))
                                 (sort-by :application/id)))))))))

(defn- group-roles-by-user [apps]
  (->> apps
       (mapcat (fn [app] (:application/user-roles app)))
       (reduce (fn [roles-by-user [user roles]]
                 (update roles-by-user user set/union roles))
               {})))

(deftest test-group-roles-by-user
  (let [apps [(-> {:application/id 1}
                  (permissions/give-role-to-users :foo ["user-1" "user-2"]))
              (-> {:application/id 2}
                  (permissions/give-role-to-users :bar ["user-1"]))]]
    (is (= {"user-1" #{:foo :bar}
            "user-2" #{:foo}}
           (group-roles-by-user apps)))))

(defn- group-users-by-role [apps]
  (->> apps
       (mapcat (fn [app]
                 (for [[user roles] (:application/user-roles app)
                       role roles]
                   [user role])))
       (reduce (fn [users-by-role [user role]]
                 (update users-by-role role conj-set user))
               {})))

(deftest test-group-users-by-role
  (let [apps [(-> {:application/id 1}
                  (permissions/give-role-to-users :foo ["user-1" "user-2"]))
              (-> {:application/id 2}
                  (permissions/give-role-to-users :bar ["user-1"]))]]
    (is (= {:foo #{"user-1" "user-2"}
            :bar #{"user-1"}}
           (group-users-by-role apps)))))

(defn refresh-all-applications-cache! []
  (events-cache/refresh!
   all-applications-cache
   (fn [state events]
     ;; Because enrich-with-injections is not idempotent,
     ;; it's necessary to hold on to the "raw" applications.
     ;; TODO: consider making enrich-with-injections idempotent (move dissocs to hide-non-public-information and other small refactorings)
     (let [raw-apps (reduce all-applications-view (::raw-apps state) events)
           updated-app-ids (distinct (map :application/id events))
           ;; TODO: batched injections: only one DB query to fetch all catalogue items etc.
           ;;       - fetch all items in the background as a batch, use plain maps as injections
           ;;       - change db/get-license and db/get-user-attributes to fetch all rows if ID is not defined
           cached-injections (map-vals memoize fetcher-injections)
           enriched-apps (->> (select-keys raw-apps updated-app-ids)
                              (map-vals #(model/enrich-with-injections % cached-injections))
                              (merge (::enriched-apps state)))]
       {::raw-apps raw-apps
        ::enriched-apps enriched-apps
        ::apps-by-user (group-apps-by-user (vals enriched-apps))
        ::roles-by-user (group-roles-by-user (vals enriched-apps))
        ::users-by-role (group-users-by-role (vals enriched-apps))}))))

(defn get-all-unrestricted-applications []
  (-> (refresh-all-applications-cache!)
      ::enriched-apps
      (vals)))

(defn get-all-applications [user-id]
  (-> (refresh-all-applications-cache!)
      (get-in [::apps-by-user user-id])
      (->> (map ->ApplicationOverview))))

(defn- get-all-applications-full [user-id] ;; full i.e. not overview
  (-> (refresh-all-applications-cache!)
      (get-in [::apps-by-user user-id])))

(defn get-all-application-roles [user-id]
  (-> (refresh-all-applications-cache!)
      (get-in [::roles-by-user user-id])
      (set)))

(defn get-users-with-role [role]
  (-> (refresh-all-applications-cache!)
      (get-in [::users-by-role role])
      (set)))

(defn- my-application? [application]
  (some #{:applicant :member} (:application/roles application)))

(defn get-my-applications [user-id]
  (->> (get-all-applications user-id)
       (filter my-application?)))

(defn export-applications-for-form-as-csv [user-id form-id language]
  (let [applications (get-all-applications-full user-id)
        filtered-applications (filter #(contains? (set (map :form/id (:application/forms %))) form-id) applications)]
    (csv/applications-to-csv filtered-applications form-id language)))

(defn reload-cache! []
  (log/info "Start rems.db.applications/reload-cache!")
  (empty-injections-cache!)
  ;; TODO: Here is a small chance that a user will experience a cache miss. Consider rebuilding the cache asynchronously and then `reset!` the cache.
  (events-cache/empty! all-applications-cache)
  (refresh-all-applications-cache!)
  (log/info "Finished rems.db.applications/reload-cache!"))

;; empty the cache occasionally in case some of the injected entities are changed
(mount/defstate all-applications-cache-reloader
  :start (scheduler/start! "all-applications-cache-reloader" reload-cache! (Duration/standardHours 1))
  :stop (scheduler/stop! all-applications-cache-reloader))

(defn delete-application!
  [app-id]
  (let [application (get-application app-id)]
    (assert (= :application.state/draft (:application/state application))
            (str "Tried to delete application " app-id " which is not a draft!")))
  (db/delete-application-attachments! {:application app-id})
  (db/delete-application-events! {:application app-id})
  (db/delete-application! {:application app-id}))

(defn delete-application-and-reload-cache! [app-id]
  (delete-application! app-id)
  (reload-cache!))

(defn reset-cache! []
  (events-cache/empty! all-applications-cache))

