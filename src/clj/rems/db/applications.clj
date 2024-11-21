(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [clojure.core.memoize :refer [memo]]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [conman.core :as conman]
            [medley.core :refer [distinct-by filter-vals map-vals]]
            [mount.core :as mount]
            [rems.application.events-cache :as events-cache]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [conj-set]]
            [rems.config :refer [env]]
            [rems.db.attachments]
            [rems.db.blacklist]
            [rems.db.catalogue]
            [rems.db.core :as db]
            [rems.db.events]
            [rems.db.form]
            [rems.db.licenses]
            [rems.db.resource]
            [rems.db.roles]
            [rems.db.users]
            [rems.db.workflow]
            [rems.permissions :as permissions]))

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
  (let [item (rems.db.catalogue/get-catalogue-item catalogue-item-id)
        resource-licenses (:licenses (rems.db.resource/get-resource (:resource-id item)))
        workflow-licenses (-> (rems.db.workflow/get-workflow (:wfid item))
                              (get-in [:workflow :licenses]))]
    (->> (concat resource-licenses workflow-licenses)
         (map #(clojure.set/rename-keys % {:id :license/id}))
         (distinct-by :license/id)
         (into []))))

(defn get-application-by-invitation-token [invitation-token]
  (:id (db/get-application-by-invitation-token {:token invitation-token})))

;;; Fetching applications (for API)

(def fetcher-injections
  {:get-attachments-for-application rems.db.attachments/get-attachments-for-application
   :get-attachment-metadata rems.db.attachments/get-attachment
   :get-form-template rems.db.form/get-form-template
   :get-catalogue-item rems.db.catalogue/get-catalogue-item
   :get-catalogue-item-licenses get-catalogue-item-licenses
   :get-config (fn [] env)
   :get-license rems.db.licenses/get-license
   :get-resource rems.db.resource/get-resource
   :get-user rems.db.users/get-user
   :get-users-with-role rems.db.roles/get-users-with-role
   :get-workflow rems.db.workflow/get-workflow
   :blacklisted? rems.db.blacklist/blacklisted?})

(defn get-application-internal
  "Returns the full application state without any user permission
   checks and filtering of sensitive information. Don't expose via APIs."
  [application-id]
  (let [events (rems.db.events/get-application-events application-id)]
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

(mount/defstate
  ^{:doc "The cached state will contain the following keys:
          ::raw-apps
          - Map from application ID to the pure projected state of an application.

          ::enriched-apps
          - Map from application ID to the enriched version of an application.
            Built from the raw apps by calling `enrich-with-injections`.
            Since the injected entities (resources, forms etc.) are mutable,
            it creates a cache invalidation problem here.

          ::app-ids-by-user
          - Map from `userid` to a set of application ids which the user can see.
            E.g. {\"alice\": #{1023, 3021, 3024, ...}, ...}

          ::roles-by-user
          - Map from `userid` to a map of all application roles which the user has
            and a count of them, a union of roles from all applications.
            E.g. {\"alice\": {:applicant 1 :member 2}, ...}

          ::users-by-role
          - Map of role to set of `userid` who has that role.
            E.g. {:applicant #{\"alice\" \"bob\" ...}}"}
  all-applications-cache
  :start (events-cache/new))

(defn- group-apps-by-user [apps]
  (->> apps
       (eduction (mapcat (fn [app]
                           (for [user (keys (:application/user-roles app))]
                             [user app]))))
       (reduce (fn [apps-by-user [user app]]
                 (if user ; test data could have a user without permissions
                   (update apps-by-user user conj app)
                   apps-by-user))
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
       (eduction (mapcat (fn [app] (:application/user-roles app))))
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
       (eduction (mapcat (fn [app]
                           (for [[user roles] (:application/user-roles app)
                                 role roles]
                             [user role]))))
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

(defn- user-roles-frequencies [applications]
  (if (seq applications)
    (let [all-user-roles (->> applications
                              (eduction (map val)
                                        (mapcat :application/user-roles)))
          roles-by-user (reduce (fn [m [user roles]]
                                  (assoc! m
                                          ::users (conj! (::users m) user)
                                          user (reduce conj!
                                                       (or (m user) (transient []))
                                                       roles)))
                                (transient {::users (transient #{})})
                                all-user-roles)
          users (persistent! (::users roles-by-user))]
      (-> (reduce (fn [m user]
                    (assoc! m user (frequencies (persistent! (m user)))))
                  roles-by-user
                  users)
          (dissoc! ::users)
          persistent!))
    {}))

(defn- update-user-roles [updated-users old-roles-by-user old-updated-enriched-apps new-updated-enriched-apps]
  (let [all-old-app-roles (user-roles-frequencies old-updated-enriched-apps)
        all-new-app-roles (user-roles-frequencies new-updated-enriched-apps)]

    (into old-roles-by-user
          (for [userid updated-users
                :let [old-user-roles (or (old-roles-by-user userid) {})
                      old-app-roles (or (all-old-app-roles userid) {})
                      new-app-roles (or (all-new-app-roles userid) {})]]

            [userid (as-> old-user-roles roles
                      (merge-with - roles old-app-roles)
                      (merge-with + roles new-app-roles)
                      (filter-vals pos? roles))]))))

(deftest update-user-roles-test
  (is (= {"alice" {:applicant 1} "bob" {:applicant 1}}
         (update-user-roles ["alice" "bob"]
                            {}
                            {1 {:application/id 1 :application/user-roles {}}
                             2 {:application/id 2 :application/user-roles {}}}
                            {1 {:application/id 1 :application/user-roles {"alice" #{:applicant}}}
                             2 {:application/id 2 :application/user-roles {"bob" #{:applicant}}}}))
      "new user rights are given")
  (is (= {"alice" {:applicant 1 :member 1} "bob" {:applicant 1}}
         (update-user-roles ["alice" "bob"]
                            {"alice" {:applicant 2} "bob" {:member 1}}
                            {1 {:application/id 1 :application/user-roles {"alice" #{:applicant}}}
                             2 {:application/id 2 :application/user-roles {"alice" #{:applicant} "bob" #{:member}}}}
                            {1 {:application/id 1 :application/user-roles {"alice" #{:applicant}}}
                             2 {:application/id 2 :application/user-roles {"alice" #{:member} "bob" #{:applicant}}}}))
      "roles are swapped in change applicant of one application")
  (is (= {"alice" {}
          "bob" {:applicant 1}}
         (update-user-roles ["alice" "bob"]
                            {"alice" {:applicant 1} "bob" {:applicant 1 :member 1}}
                            {1 {:application/id 1 :application/user-roles {"bob" #{:applicant}}}
                             2 {:application/id 2 :application/user-roles {"alice" #{:applicant} "bob" #{:member}}}}
                            {1 {:application/id 1 :application/user-roles {}}
                             2 {:application/id 2 :application/user-roles {"bob" #{:applicant}}}}))
      "roles can be removed"))

(defn- update-cache [{:keys [state updated-app-ids deleted-app-ids events]}]
  ;; terminology:
  ;; - old          - all from previous round
  ;; - new          - new results from this round
  ;; - raw          - not enriched (no injections)
  ;; - personalized - app for user (no :application/user-roles)
  ;; - updated      - only those that changed this round
  ;; - deleted      - applications that are going away
  (let [updated-app-ids (set (into updated-app-ids deleted-app-ids)) ; let's consider deleted to be automatically an app to update

        ;; temporarily cached injections because a mass update may have a lot of same calls (e.g. handlers of the workflow of many applications)
        cached-get-user (memo (:get-user fetcher-injections))
        cached-get-workflow-handlers (memo (fn [workflow-id]
                                             (model/get-workflow-handlers workflow-id
                                                                          (:get-workflow fetcher-injections)
                                                                          cached-get-user)))
        cached-injections (assoc fetcher-injections
                                 :get-user cached-get-user
                                 :cached-get-workflow-handlers cached-get-workflow-handlers)

        old-raw-apps (::raw-apps state)
        old-enriched-apps (::enriched-apps state)
        old-updated-enriched-apps (select-keys old-enriched-apps updated-app-ids)
        old-updated-apps-by-user (group-apps-by-user (vals old-updated-enriched-apps))
        old-app-ids-by-user (::app-ids-by-user state)
        old-roles-by-user (::roles-by-user state {})
        old-users-by-role (::users-by-role state)

        new-raw-apps (as-> old-raw-apps apps
                       (reduce all-applications-view apps events)
                       (apply dissoc apps deleted-app-ids)
                       (doall apps))
        new-updated-raw-apps (select-keys new-raw-apps updated-app-ids)
        new-updated-enriched-apps (doall (map-vals #(model/enrich-with-injections % cached-injections) new-updated-raw-apps))
        new-enriched-apps (as-> new-updated-enriched-apps apps
                            (merge old-enriched-apps apps)
                            (apply dissoc apps deleted-app-ids)
                            (doall apps))
        new-updated-apps-by-user (group-apps-by-user (vals new-updated-enriched-apps))

        ;; we need to update the users related to the old and new applications in this round
        updated-users (set/union (set (keys old-updated-apps-by-user))
                                 (set (keys new-updated-apps-by-user)))

        new-app-ids-by-user (doall (reduce (fn [old-app-ids-by-user userid]
                                             (update old-app-ids-by-user userid
                                                     (fn [old-app-ids]
                                                       (into (apply disj (set old-app-ids) updated-app-ids)
                                                             (map :application/id (get new-updated-apps-by-user userid))))))
                                           old-app-ids-by-user
                                           updated-users))

        ;; update all the users that are in this round
        new-roles-by-user (update-user-roles updated-users
                                             old-roles-by-user
                                             old-updated-enriched-apps
                                             new-updated-enriched-apps)

        ;; now calculate the reverse, i.e. users by role
        new-updated-users-by-role (->> (for [userid updated-users
                                             [role _] (new-roles-by-user userid)]
                                         {role #{userid}})
                                       (apply merge-with set/union))
        ;; update all the users that are in this round
        new-users-by-role (->> old-users-by-role
                               ;; remove users updated in this round
                               (map-vals (fn [users] (apply disj users updated-users)))
                               ;; add users back to correct groups
                               (merge-with set/union new-updated-users-by-role)
                               doall)]

    {::raw-apps new-raw-apps
     ::enriched-apps new-enriched-apps
     ::app-ids-by-user new-app-ids-by-user
     ::roles-by-user new-roles-by-user
     ::users-by-role new-users-by-role}))

(defn refresh-all-applications-cache! []
  (events-cache/refresh!
   all-applications-cache
   (fn [state events]
     (update-cache {:state state
                    :updated-app-ids (set (map :application/id events))
                    :events events}))))

(defn- update-in-all-applications-cache! [app-ids]
  (events-cache/update-cache! all-applications-cache
                              (fn [state]
                                (update-cache {:state state
                                               :updated-app-ids (set app-ids)}))))

(defn- delete-from-all-applications-cache! [app-id]
  (events-cache/update-cache! all-applications-cache
                              (fn [state]
                                (update-cache {:state state
                                               :deleted-app-ids (set [app-id])}))))

(defn get-all-unrestricted-applications []
  (-> (refresh-all-applications-cache!)
      ::enriched-apps
      (vals)))

(defn get-all-applications-full
  "Returns all full, personalized applications for `userid`. Optional `xf` can be applied 
   to list of application ids before transformation, e.g. search filter."
  [userid & [xf]] ; full i.e. not overview
  (let [cache (refresh-all-applications-cache!)
        app-ids (get-in cache [::app-ids-by-user userid])
        cached-apps (get-in cache [::enriched-apps])
        personalize-app (fn [app] ; NB: may return nil if should not see the app
                          (model/apply-user-permissions app userid))]
    (cond->> app-ids
      xf (eduction xf)
      true (eduction (map cached-apps)
                     (keep personalize-app)))))

(defn- my-application? [app]
  (some #{:applicant :member} (:application/roles app)))

(defn get-my-applications-full
  "Returns all full, personalized applications where `userid` is an applying user.
   Optional `xf` can be applied to list of application ids before transformation, e.g. search filter."
  [userid & [xf]]
  (->> (get-all-applications-full userid xf)
       (eduction (filter my-application?))))

(defn get-all-application-roles [userid]
  (-> (refresh-all-applications-cache!)
      (get-in [::roles-by-user userid])
      keys
      set))

(defn get-users-with-role [role]
  (-> (refresh-all-applications-cache!)
      (get-in [::users-by-role role])
      set))

(defn reload-cache! []
  (log/info "Start rems.db.applications/reload-cache!")
  ;; TODO: Here is a small chance that a user will experience a cache miss. Consider rebuilding the cache asynchronously and then `reset!` the cache.
  (events-cache/empty! all-applications-cache)
  (refresh-all-applications-cache!)
  (log/info "Finished rems.db.applications/reload-cache!"))

(defn reload-applications! [{:keys [by-userids by-workflow-ids]}]
  ;; NB: try make sure the cache is up to date so we have any new applications present
  (when (seq by-userids)
    (let [apps (refresh-all-applications-cache!)
          app-ids (->> by-userids
                       (mapcat (fn [by-userid]
                                 (->> (get-in apps [::app-ids-by-user by-userid])
                                      (mapv :application/id))))
                       distinct)]
      (log/info "Reloading" (count app-ids) "applications because of user changes")
      (when (seq app-ids)
        (update-in-all-applications-cache! app-ids))))

  (when (seq by-workflow-ids)
    (let [apps (refresh-all-applications-cache!)
          wf-ids (set by-workflow-ids)
          app-ids (->> (::enriched-apps apps)
                       vals
                       (filter (comp wf-ids :workflow/id :application/workflow))
                       (mapv :application/id))]
      (log/info "Reloading" (count app-ids) "applications because of workflow changes")
      (when (seq app-ids)
        (update-in-all-applications-cache! app-ids))))

  nil)

(defn delete-application!
  [app-id]
  (refresh-all-applications-cache!) ; NB: try make sure the cache is up to date so we have any new applications present
  (assert (application-util/draft? (get-application app-id))
          (str "Tried to delete application " app-id " which is not a draft!"))
  (delete-from-all-applications-cache! app-id)
  (rems.db.attachments/delete-application-attachments! app-id)
  (rems.db.events/delete-application-events! app-id)
  (let [result (db/delete-application! {:application app-id})]
    (log/infof "Finished deleting application %s" app-id)
    result))

(defn reset-cache! []
  (events-cache/empty! all-applications-cache))
