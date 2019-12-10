(ns rems.db.blacklist
  (:require [rems.api.schema :refer [EventBase UserId]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [schema.utils])
  (:import (org.joda.time DateTime)))

(s/defschema BlacklistEvent
  (assoc EventBase
         :event/type (s/enum :blacklist.event/add :blacklist.event/remove)
         :userid UserId
         :resource/ext-id s/Str
         :event/comment (s/maybe s/Str)))

(def ^:private coerce-event
  (coerce/coercer BlacklistEvent json/coercion-matcher))

(defn- json->event [json]
  (let [result (-> json
                   json/parse-string
                   coerce-event)]
    (when (schema.utils/error? result)
      (throw (ex-info (str "Value does not match schema: " (pr-str result))
                      {:value json :error result})))
    result))

(defn- event-from-db [event]
  (assoc (json->event (:eventdata event))
         :event/id (:event/id event)))

(defn get-events [params]
  (mapv event-from-db (db/get-blacklist-events (select-keys params [:userid :resource/ext-id]))))

(defn- events->blacklist [events]
  ;; TODO: move computation to db for performance
  ;; should be enough to check latest event per user-resource pair
  (vals
   (reduce (fn [blacklist event]
             (let [key (select-keys event [:userid :resource/ext-id])]
               (case (:event/type event)
                 :blacklist.event/add
                 (assoc blacklist key event)
                 :blacklist.event/remove
                 (dissoc blacklist key))))
           {}
           events)))

(defn get-blacklist [params]
  (vec (sort-by (juxt :userid :resource/ext-id) (events->blacklist (get-events params)))))

(defn blacklisted? [userid resource]
  (not (empty? (get-blacklist {:userid userid
                               :resource/ext-id resource}))))
