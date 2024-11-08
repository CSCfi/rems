(ns rems.db.blacklist
  (:require [rems.cache :as cache]
            [rems.common.util :refer [apply-filters build-index getx]]
            [rems.db.core :as db]
            [rems.db.resource]
            [rems.db.users]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [schema.utils])
  (:import (org.joda.time DateTime)))

(s/defschema BlacklistEvent
  {(s/optional-key :event/id) s/Int
   :event/type (s/enum :blacklist.event/add :blacklist.event/remove)
   :event/time DateTime
   :event/actor schema-base/UserId
   :userid schema-base/UserId
   :resource/ext-id s/Str
   :event/comment (s/maybe s/Str)})

(def ^:private coerce-event
  (coerce/coercer! BlacklistEvent json/coercion-matcher))

(def ^:private validate-blacklist-event
  (s/validator BlacklistEvent))

(defn- event->json [event]
  (json/generate-string (validate-blacklist-event event)))

(defn- parse-blacklist-event-raw [x]
  (let [data (json/parse-string (:eventdata x))
        event {:event/id (:event/id x)
               :event/type (:event/type data)
               :event/time (:event/time data)
               :event/actor (:event/actor data)
               :event/comment (:event/comment data)
               :resource/ext-id (:resource/ext-id data)
               :userid (:userid data)}]
    (coerce-event event)))

(def blacklist-event-cache
  (cache/basic {:id ::blacklist-event-cache
                :miss-fn (fn [id]
                           (if-let [event (db/get-blacklist-event {:id id})]
                             (parse-blacklist-event-raw event)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-blacklist-events)
                                  (build-index {:keys [:event/id]
                                                :value-fn parse-blacklist-event-raw})
                                  (into (sorted-map))))}))

(defn get-events [& [params]]
  (->> (vals (cache/entries! blacklist-event-cache))
       (apply-filters params)))

(defn add-event! [event]
  (let [id (:id (db/add-blacklist-event! {:eventdata (event->json event)}))]
    (cache/miss! blacklist-event-cache id)
    id))

(defn update-event! [event]
  (let [id (getx event :event/id)]
    (db/update-blacklist-event! {:id id :eventdata (event->json event)})
    (cache/miss! blacklist-event-cache id)
    id))

(defn get-blacklist [params]
  (->> (get-events params)
       (reduce (fn [blacklist event]
                 (let [key (select-keys event [:userid :resource/ext-id])]
                   (case (:event/type event)
                     :blacklist.event/add (assoc blacklist key event)
                     :blacklist.event/remove (dissoc blacklist key))))
               {})
       vals
       (sort-by (juxt :userid :resource/ext-id))))

(defn blacklisted? [userid resource]
  (seq (get-blacklist {:userid userid :resource/ext-id resource})))
