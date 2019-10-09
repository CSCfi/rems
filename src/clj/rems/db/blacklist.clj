(ns rems.db.blacklist
  (:require [clj-time.format :as time-format]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [schema.utils])
  (:import (org.joda.time DateTime)))

;; TODO copied from rems.application.events:
(def UserId s/Str)

(def ResourceId s/Str)

(def BlacklistEvent
  {(s/optional-key :event/id) s/Int
   :event/type (s/enum :blacklist.event/add :blacklist.event/remove)
   :event/time DateTime
   :event/actor UserId
   :blacklist/user UserId
   :blacklist/resource ResourceId ;; resource/ext-id
   :event/comment (s/maybe s/Str)})

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

(defn- event->json [event]
  (s/validate BlacklistEvent event)
  (json/generate-string event))

(defn add-event! [event]
  (db/add-blacklist-event! {:eventdata (event->json event)}))

(defn- event-from-db [event]
  (assoc (json->event (:eventdata event))
         :event/id (:id event)))

(defn get-events [params]
  (mapv event-from-db (db/get-blacklist-events {:user (:blacklist/user params)
                                                :resource (:blacklist/resource params)})))

(defn- events->blacklist [events]
  ;; TODO: move computation to db for performance
  ;; should be enough to check latest event per user-resource pair
  (reduce (fn [blacklist event]
            (let [entry {:resource (:blacklist/resource event)
                         :user (:blacklist/user event)}]
              (case (:event/type event)
                :blacklist.event/add
                (conj blacklist entry)
                :blacklist.event/remove
                (disj blacklist entry))))
          #{}
          events))

(defn get-blacklist [params]
  (vec (sort-by (juxt :resource :user) (events->blacklist (get-events params)))))
