(ns rems.db.blacklist
  (:require [rems.common.util :refer [getx]]
            [rems.db.core :as db]
            [rems.db.resource :as resource]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [schema.utils])
  (:import (org.joda.time DateTime)))

(def ResourceId s/Str)

(s/defschema BlacklistEvent
  {(s/optional-key :event/id) s/Int
   :event/type (s/enum :blacklist.event/add :blacklist.event/remove)
   :event/time DateTime
   :event/actor schema-base/UserId
   :userid schema-base/UserId
   :resource/ext-id ResourceId
   :event/comment (s/maybe s/Str)})

(def ^:private coerce-event
  (coerce/coercer! BlacklistEvent json/coercion-matcher))

(defn- json->event [json]
  (-> json
      json/parse-string
      coerce-event))

(def ^:private validate-blacklist-event
  (s/validator BlacklistEvent))

(defn- event->json [event]
  (json/generate-string (validate-blacklist-event event)))

(defn- event-from-db [event]
  (assoc (json->event (:eventdata event))
         :event/id (:event/id event)))

(defn- check-foreign-keys [event]
  ;; TODO: These checks could be moved to the database as (1) constraint checks or (2) fields with foreign keys.
  (when-not (users/user-exists? (:userid event))
    (throw (IllegalArgumentException. "user doesn't exist")))
  (when-not (resource/ext-id-exists? (:resource/ext-id event))
    (throw (IllegalArgumentException. "resource doesn't exist")))
  event)

(defn add-event! [event]
  (db/add-blacklist-event! {:eventdata (-> event check-foreign-keys event->json)}))

(defn update-event! [event]
  (db/update-blacklist-event! {:id (getx event :event/id)
                               :eventdata (-> event check-foreign-keys event->json)}))

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
