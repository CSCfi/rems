(ns rems.db.events
  (:require [clojure.tools.logging :as log]
            [com.rpl.specter :refer [ALL multi-transform multi-path terminal must]]
            [medley.core :refer [update-existing]]
            [rems.application.events :as events]
            [rems.common.util :refer [build-index conj-sorted-set disj-sorted-set to-sorted-set]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema-tools.core :as st]
            [schema.coerce :as coerce]
            [schema.utils]))

(def ^:private coerce-event-commons
  (coerce/coercer! (st/open-schema schema-base/EventBase) json/coercion-matcher))

(def ^:private coerce-event-specifics
  (coerce/coercer! events/Event json/coercion-matcher))

(defn- schema-coerce-event [event]
  ;; must coerce the common fields first, so that dynamic/Event can choose the right event schema based on the event type
  (-> event
      coerce-event-commons
      coerce-event-specifics))



(defn coerce-duo-restrictions [restrictions]
  (mapv #(update-existing % :type keyword)
        restrictions))

(defn- coerce-duo-codes [duos]
  (mapv #(update-existing % :restrictions coerce-duo-restrictions)
        duos))

(defn- manual-coerce-event [event]
  (assert (:event/type event)) ; sanity checking like in Schema coerce
  (assert (:event/time event))
  (-> event
      (update :event/type keyword)
      (update :event/time rems.json/datestring->datetime)
      (update-existing :event/public boolean)
      (update-existing :entitlement/end rems.json/datestring->datetime)
      (update-existing :workflow/type keyword)
      (update-existing :application/expires-on rems.json/datestring->datetime)
      (update-existing :application/decision keyword)
      (update-existing :application/request-id #(java.util.UUID/fromString ^String %))
      (update-existing :application/accepted-licenses set)
      (update-existing :application/duo-codes coerce-duo-codes)))

(defn- specter-coerce-event [event]
  (assert (:event/type event)) ; sanity checking like in Schema coerce
  (assert (:event/time event))
  (multi-transform (multi-path [:event/type (terminal keyword)]
                               [:event/time (terminal rems.json/datestring->datetime)]
                               [(must :event/public) (terminal boolean)]
                               [(must :entitlement/end) (terminal rems.json/datestring->datetime)]
                               [(must :workflow/type) (terminal keyword)]
                               [(must :application/expires-on) (terminal rems.json/datestring->datetime)]
                               [(must :application/decision) (terminal keyword)]
                               [(must :application/request-id) (terminal #(when % (java.util.UUID/fromString ^String %)))]
                               [(must :application/accepted-licenses) (terminal set)]
                               [(must :application/duo-codes) ALL (must :restrictions) ALL (must :type) (terminal keyword)])
                   event))

(defn schema-json->event [json]
  (when json
    (schema-coerce-event (json/parse-string json))))

(defn manual-json->event [json]
  (when json
    (manual-coerce-event (json/parse-string json))))

(defn specter-json->event [json]
  (when json
    (specter-coerce-event (json/parse-string json))))

(defn event->json [event]
  (events/validate-event event)
  (json/generate-string event))

(defn- specter-fix-event-from-db [event]
  (assoc (specter-json->event (:eventdata event))
         :event/id (:id event)))

(def low-level-event-cache (atom ::uninitialized))
(def events-by-application-cache (atom ::uninitialized))

(defn reset-event-cache! []
  (reset! low-level-event-cache ::uninitialized)
  (reset! events-by-application-cache ::uninitialized))

(defn ensure-cache-is-initialized! []
  (when (= ::uninitialized @low-level-event-cache)
    (log/info "Initializing low level event cache")
    (let [events (db/get-application-events-since {:id 0})]
      (log/info "Found" (count events) "events")
      (reset! low-level-event-cache (sorted-map))
      (doseq [event events]
        (let [fixed-event (specter-fix-event-from-db event)
              id (:event/id fixed-event)]
          (log/debug "Loading uncached event:" id)
          (swap! low-level-event-cache assoc id fixed-event)))
      (reset! events-by-application-cache (build-index {:keys [:application/id]
                                                        :value-fn :event/id
                                                        :collect-fn to-sorted-set}
                                                       (vals @low-level-event-cache))))))

(defn fix-event-from-db [event]
  (locking low-level-event-cache
    (ensure-cache-is-initialized!)
    (let [id (:id event)]
      (if-some [cached-event (get @low-level-event-cache id)]
        cached-event

        (let [_ (log/debug "Loading uncached event:" id)
              fixed-event (specter-fix-event-from-db event)]
          (swap! low-level-event-cache assoc id fixed-event)
          (swap! events-by-application-cache update (:application/id fixed-event) conj-sorted-set id)
          fixed-event)))))

(defn- get-all-events-internal []
  (locking low-level-event-cache
    (ensure-cache-is-initialized!)
    @low-level-event-cache))

(defn get-application-events [application-id]
  (locking low-level-event-cache
    (ensure-cache-is-initialized!)
    (->> application-id
         (@events-by-application-cache)
         (mapv @low-level-event-cache))))

(defn get-all-events-since [event-id]
  (-> (get-all-events-internal)
      (subseq > event-id)
      vals))

(defn get-latest-event []
  (some-> (get-all-events-internal)
          last
          val))

(defn add-event!
  "Add `event` to database. Returns the event as it went into the db."
  [event]
  (let [new-event (fix-event-from-db (db/add-application-event! {:application (:application/id event)
                                                                 :eventdata (event->json event)}))]
    (locking low-level-event-cache
      (ensure-cache-is-initialized!)
      (swap! low-level-event-cache assoc (:event/id new-event) new-event)
      (swap! events-by-application-cache update (:application/id new-event) conj-sorted-set (:event/id new-event)))

    new-event))

(defn update-event!
  "Updates an event on top of an old one. Returns the event as it went into the db."
  [event]
  (let [old-event (fix-event-from-db (first (db/get-application-event {:id (:event/id event)})))
        _ (assert old-event)
        new-event (merge old-event event)]
    (db/update-application-event! {:id (:event/id old-event)
                                   :application (:application/id new-event)
                                   :eventdata (event->json (dissoc new-event :event/id))})
    (locking low-level-event-cache
      (ensure-cache-is-initialized!)
      (swap! low-level-event-cache assoc (:event/id old-event) new-event)
      (swap! events-by-application-cache update (:application/id new-event) conj-sorted-set (:event/id old-event)))

    new-event))

(defn replace-event!
  "Replaces an event on top of an old one.

  Differs from `add-event!` in that it replaces an old event.
  Differs from `update-event!` in that the event id is a new one.

  Returns the event as it went into the db."
  [event]
  (let [old-event (fix-event-from-db (first (db/get-application-event {:id (:event/id event)}))) ; TODO: not necessary to get
        _ (assert old-event)
        event (merge old-event event)
        new-event (fix-event-from-db (db/replace-application-event! {:id (:event/id old-event)
                                                                     :application (:application/id event)
                                                                     :eventdata (event->json (dissoc event :event/id))}))]
    (locking low-level-event-cache
      (ensure-cache-is-initialized!)
      (swap! low-level-event-cache assoc (:event/id new-event) new-event)
      (swap! low-level-event-cache dissoc (:event/id old-event))
      (swap! events-by-application-cache update (:application/id old-event) disj-sorted-set (:event/id old-event))
      (swap! events-by-application-cache update (:application/id new-event) conj-sorted-set (:event/id new-event)))

    new-event))

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
      (add-event! event))))

(defn delete-application-events! [app-id]
  (let [application-events (get-application-events app-id)]
    (db/delete-application-events! {:application app-id})
    (locking low-level-event-cache
      (ensure-cache-is-initialized!)
      (swap! low-level-event-cache
             (fn [cached-events]
               (reduce (fn [cached-events application-event]
                         (dissoc cached-events (:event/id application-event)))
                       cached-events
                       application-events)))
      (swap! events-by-application-cache dissoc app-id))))
