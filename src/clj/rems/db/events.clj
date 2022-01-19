(ns rems.db.events
  (:require [rems.application.events :as events]
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
  (map fix-event-from-db (db/get-application-events {:application application-id})))

(defn get-all-events-since [event-id]
  (map fix-event-from-db (db/get-application-events-since {:id event-id})))

(defn get-latest-event []
  (fix-event-from-db (db/get-latest-application-event {})))

(defn add-event!
  "Add event to database. Returns the event as it went into the db."
  [event]
  (fix-event-from-db (db/add-application-event! {:application (:application/id event)
                                                 :eventdata (event->json event)})))

(defn update-event!
  "Updates an event on top of an old one. Returns the event as it went into the db."
  [event]
  (let [old-event (fix-event-from-db (first (db/get-application-event {:id (:event/id event)})))
        _ (assert old-event)
        event (-> old-event
                  (merge event)
                  (dissoc :event/id))]
    (fix-event-from-db (db/update-application-event! {:id (:event/id old-event)
                                                      :application (:application/id event)
                                                      :eventdata (event->json event)}))))
