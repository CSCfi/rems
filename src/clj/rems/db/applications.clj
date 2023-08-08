(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [clojure.java.jdbc :as jdbc]
            [conman.core :as conman]
            [medley.core :refer [filter-vals]]
            [rems.application.commands :as commands]
            [rems.application.events :as events]
            [rems.cache]
            [rems.common.util :refer [conj-set disj-set]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [rems.application.model :as model]
            [schema-tools.core :as st]
            [schema.coerce :as coerce]
            [schema.utils])
  (:import rems.TryAgainException))

;;; Cache

(defn reload-cache! []
  ;; TODO implement
  )

;;; Events

(def ^:private coerce-event-commons
  (coerce/coercer! (st/open-schema schema-base/EventBase) json/coercion-matcher))

(def ^:private coerce-event-specifics
  (coerce/coercer! events/Event json/coercion-matcher))

(defn- coerce-event [event]
  ;; must coerce the common fields first, so that dynamic/Event can choose the right event schema based on the event type
  (-> event
      coerce-event-commons
      coerce-event-specifics))

(defn json->event [json]
  (when json
    (coerce-event (json/parse-string json))))

(defn event->json [event]
  (events/validate-event event)
  (json/generate-string event))

(defn- fix-event-from-db [event]
  (assoc (-> event :eventdata json->event)
         :event/id (:id event)))

(defn get-application-events [application-id]
  (assert application-id)
  (->> {:application application-id}
       db/get-application-events
       (mapv fix-event-from-db)))

(defn get-all-events-since [event-id]
  (mapv fix-event-from-db (db/get-application-events-since {:id event-id})))

(defn get-latest-event []
  (fix-event-from-db (db/get-latest-application-event {})))

(defn- user-applications-projection [user-applications event application]
  (let [actor (:event/actor event)
        app-id (:application/id event)]
    (case (:event/type event)
      (:application.event/attachments-redacted
       :application.event/applicant-changed
       :application.event/approved
       :application.event/closed
       :application.event/review-requested
       :application.event/reviewed
       :application.event/copied-to
       :application.event/decided
       :application.event/decider-invited
       :application.event/decision-requested
       :application.event/deleted
       :application.event/draft-saved
       :application.event/external-id-assigned
       :application.event/expiration-notifications-sent
       :application.event/licenses-accepted
       :application.event/licenses-added
       :application.event/member-invited
       :application.event/member-uninvited
       :application.event/rejected
       :application.event/remarked
       :application.event/resources-changed
       :application.event/returned
       :application.event/reviewer-invited
       :application.event/revoked
       :application.event/submitted)
      user-applications ; no user added

      :application.event/copied-from
      (reduce (fn [user-applications userid]
                (update user-applications userid app-id))
              user-applications
              (:application/members application))

      :application.event/created
      (update user-applications actor conj-set app-id)

      :application.event/decider-joined
      (update user-applications actor conj-set app-id)

      :application.event/member-added
      (update user-applications actor conj-set (:userid (:application/member event)))

      :application.event/member-joined
      (update user-applications actor conj-set app-id)

      :application.event/member-removed
      (update user-applications actor disj-set (:userid (:application/member event)))

      :application.event/reviewer-joined
      (update user-applications actor conj-set app-id))))

(defn- user-roles-projection [user-roles event application]
  (let [actor (:event/actor event)]
    (case (:event/type event)
      (:application.event/attachments-redacted
       :application.event/approved
       :application.event/closed
       :application.event/copied-from
       :application.event/copied-to
       :application.event/decider-invited
       :application.event/deleted
       :application.event/draft-saved
       :application.event/external-id-assigned
       :application.event/expiration-notifications-sent
       :application.event/licenses-accepted
       :application.event/licenses-added
       :application.event/member-invited
       :application.event/member-uninvited
       :application.event/rejected
       :application.event/remarked
       :application.event/resources-changed
       :application.event/returned
       :application.event/reviewer-invited
       :application.event/revoked
       :application.event/submitted)
      user-roles ; no user role added

      :application.event/reviewed
      (update user-roles actor (fn [roles]
                                 (-> roles
                                     (disj-set :reviewer)
                                     (conj-set :past-reviewer))))

      :application.event/review-requested
      (reduce (fn [user-roles reviewer]
                (update user-roles reviewer conj-set :reviewer))
              user-roles
              (:application/reviewers event))

      :application.event/applicant-changed
      (-> user-roles
          (update (:application/applicant application) disj-set :applicant)
          (update (:application/applicant event) (conj-set :applicant)))

      :application.event/decided
      (update user-roles actor (fn [roles]
                                 (-> roles
                                     (disj-set :decider)
                                     (conj-set :past-decider))))

      :application.event/decision-requested
      (reduce (fn [user-roles decider]
                (update user-roles decider conj-set :decider))
              user-roles
              (:application/deciders event))

      :application.event/created
      (update user-roles actor conj-set :applicant)

      :application.event/decider-joined
      (update user-roles actor conj-set :decider)

      :application.event/member-added
      (update user-roles (:userid (:application/member event)) conj-set :member)

      :application.event/member-joined
      (update user-roles actor conj-set :member)

      :application.event/member-removed
      (update user-roles (:userid (:application/member event)) disj-set :member)

      :application.event/reviewer-joined
      (update user-roles actor conj-set :reviewer))))

(def projections (atom nil))

(defn- update-projections [event application]
  (swap! projections (fn [projections]
                       (-> projections
                           (update :projection/user-applications user-applications-projection event application)
                           (update :projection/user-roles user-roles-projection event application))))
  event)

(defn get-users-with-role [role]
  ;; TODO optimize
  (set (keys (filter-vals #(contains? % role) (:projection/user-roles @projections)))))

(defn get-all-application-roles [userid]
  (get (:projection/user-roles @projections) userid #{}))

(defn add-event!
  "Add `event` of the `application` to database.

  Updates the projections regarding the application.

  Returns the event as it went into the db."
  [application event]
  (-> {:application (:application/id event)
       :eventdata (event->json event)}
      db/add-application-event!
      fix-event-from-db
      (update-projections application)))

(defn update-event!
  "Updates an event on top of an old one. Returns the event as it went into the db."
  [event]
  (let [old-event (fix-event-from-db (first (db/get-application-event {:id (:event/id event)})))
        _ (assert old-event)
        event (merge old-event event)]
    (db/update-application-event! {:id (:event/id old-event)
                                   :application (:application/id event)
                                   :eventdata (event->json (dissoc event :event/id))})
    event))

(defn replace-event!
  "Replaces an event on top of an old one.

  Differs from `add-event!` in that it replaces an old event.
  Differs from `update-event!` in that the event id is a new one.

  Returns the event as it went into the db."
  [event]
  (let [old-event (fix-event-from-db (first (db/get-application-event {:id (:event/id event)})))
        _ (assert old-event)
        event (merge old-event event)]
    (fix-event-from-db (db/replace-application-event! {:id (:event/id old-event)
                                                       :application (:application/id event)
                                                       :eventdata (event->json (dissoc event :event/id))}))))

(defn add-event-with-compaction!
  "Add `event` to database.

  Consecutive `:draft-saved` events of `application` are compacted into one by replacing
  the last event of `application` instead of creating a new one.

  Returns the event as it went into the db."
  [application event]
  (let [last-event (-> application
                       :application/events
                       last)]
    (if (and (:enable-save-compaction env)
             (= :application.event/draft-saved
                (:event/type event)
                (:event/type last-event))
             (= (:application/id event)
                (:application/id last-event)))
      (replace-event! (merge event
                             (select-keys last-event [:event/id])))
      (add-event! application event))))

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

(defmacro one-at-a-time! [& body]
  ;; Use locks to prevent multiple commands being executed in parallel.
  ;; Serializable isolation level will already avoid anomalies, but produces
  ;; lots of transaction conflicts when there is contention. This lock
  ;; roughly doubles the throughput for rems.db.test-transactions tests.
  ;;
  ;; To clarify: without this lock our API calls would sometimes fail
  ;; with transaction conflicts due to the serializable isolation
  ;; level. The transaction conflict exceptions aren't handled
  ;; currently and would cause API calls to fail with HTTP status 500.
  ;; With this lock, API calls block more but never result in
  ;; transaction conflicts.
  ;;
  ;; See docs/architecture/010-transactions.md for more info.
  `(try
     (jdbc/execute! db/*db* ["LOCK TABLE application_event IN SHARE ROW EXCLUSIVE MODE"])
     ~@body

     (catch org.postgresql.util.PSQLException e#
       (if (.contains (.getMessage e#) "lock timeout")
         (throw (TryAgainException. e#))
         (throw e#)))))

(defn command! [cmd application command-injections]
  (commands/handle-command cmd application command-injections))

(defn get-application-id-by-invitation-token [invitation-token]
  (:id (db/get-application-id-by-invitation-token {:token invitation-token})))

;;; Listing all applications

(defn get-simple-applications
  "Gets simple shallow version of all applications. Useful for lists."
  []
  (->> (db/get-application-events)
       (mapv fix-event-from-db)
       (group-by :application/id)
       vals
       (mapv model/build-application-view)
       (sort-by :application/id)))

(defn get-applications-with-user [userid]
  (throw (RuntimeException. "get-applications-with-user not implemented")))

(defn get-simple-internal-application
  "Simple application state with some internal information. Not personalized for any users. Not suitable for public APIs."
  [application-id]
  (some-> application-id
          get-application-events
          seq
          model/build-application-view))

(defn get-simple-internal-applications []
  (->> (get-simple-applications)
       (mapv (comp get-simple-internal-application :application/id))))

(defn get-simple-internal-applications-by-user [userid]
  (->> (get-simple-internal-applications)
       (filterv (fn [application]
                  (or (= (-> application :application/applicant :userid) userid)
                      (contains? (set (map :userid (:application/members application))) userid))))))

(defn delete-application!
  [app-id]
  ;; XXX: additional safety measure for now
  ;; consider removing when old applications need to expire
  (let [application (get-simple-internal-application app-id)]
    (assert (= :application.state/draft (:application/state application))
            (str "Tried to delete application " app-id " which is not a draft!")))

  (db/delete-application-attachments! {:application app-id})
  (db/delete-application-events! {:application app-id})
  (db/delete-application! {:application app-id}))


(defn reload-projections! []
  (doseq [event (get-all-events-since 0)]
    (update-projections event (get-simple-internal-application (:application/id event)))))

(comment
  (reload-projections!))

