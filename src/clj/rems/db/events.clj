(ns rems.db.events
  (:require [clj-time.format :as time-format]
            [rems.application.events :as events]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.utils]
            [schema-tools.core :as st])
  (:import [org.joda.time DateTime]))

(def ^:private coerce-event-commons
  (coerce/coercer (st/open-schema events/EventBase) json/coercion-matcher))

(def ^:private coerce-event-specifics
  (coerce/coercer events/Event json/coercion-matcher))

(defn- coerce-event [event]
  ;; must coerce the common fields first, so that dynamic/Event can choose the right event schema based on the event type
  (-> event
      coerce-event-commons
      coerce-event-specifics))

(defn json->event [json]
  (when json
    (let [result (coerce-event (json/parse-string json))]
      (when (schema.utils/error? result)
        ;; similar exception as what schema.core/validate throws
        (throw (ex-info (str "Value does not match schema: " (pr-str result))
                        {:schema events/Event :value json :error result})))
      result)))

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

(defn add-event! [event]
  (db/add-application-event! {:application (:application/id event)
                              :eventdata (event->json event)})
  nil)
