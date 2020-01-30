(ns rems.db.events
  (:require [rems.application.events :as events]
            [rems.db.core :as db]
            [rems.json :as json]
            [medley.core :refer [map-keys]]
            [rems.util :refer [update-present]]
            [schema-tools.core :as st]
            [schema.coerce :as coerce]
            [schema.utils]))

(def ^:private coerce-event-commons
  (coerce/coercer! (st/open-schema events/EventBase) json/coercion-matcher))

(def ^:private coerce-event-specifics
  (coerce/coercer! events/Event json/coercion-matcher))

(defn- fix-field-values
  "Fixes the keys of `:application/field-values`.

  They are of type {s/Str s/Str} which means they will be
  converted by jsonista to {s/Keyword s/Str} and need to be
  transformed into strings.

  Test generators creates random strings i.e. \"foo/bar\",
  a namespaced keyword that is. Therefore we can't use `name`
  and must use substring of `str`."
  [event]
  (update-present event :application/field-values (partial map-keys #(subs (str %) 1))))

(defn- coerce-event [event]
  ;; must coerce the common fields first, so that dynamic/Event can choose the right event schema based on the event type
  (-> event
      fix-field-values
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

(defn add-event! [event]
  (db/add-application-event! {:application (:application/id event)
                              :eventdata (event->json event)})
  nil)
