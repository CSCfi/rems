(ns rems.api.services.blacklist
  (:require [clj-time.core :as time]
            [rems.application.events :as events]
            [rems.db.blacklist :as blacklist]
            [rems.db.core :as db]
            [rems.db.resource :as resource]
            [rems.db.users :as users]
            [rems.json :as json]
            [schema.core :as s]))

(defn- event->json [event]
  (s/validate blacklist/BlacklistEvent event)
  (json/generate-string event))

(defn- check-foreign-keys [event]
  ;; TODO: These checks could be moved to the database as (1) constraint checks or (2) fields with foreign keys.
  (when-not (users/user-exists? (:userid event))
    (throw (IllegalArgumentException. "user doesn't exist")))
  (when-not (resource/ext-id-exists? (:resource/ext-id event))
    (throw (IllegalArgumentException. "resource doesn't exist")))
  event)

(defn- command->event [command user-id]
  {:event/actor user-id
   :event/time (time/now)
   :userid (get-in command [:blacklist/user :userid])
   :resource/ext-id (get-in command [:blacklist/resource :resource/ext-id])
   :event/comment (:comment command)})

(defn add-event! [event]
  (db/add-blacklist-event! {:eventdata (-> event check-foreign-keys event->json)}))

(defn add-user-to-blacklist! [user-id command]
  (add-event! (-> (command->event command user-id)
                  (assoc :event/type :blacklist.event/add))))

(defn remove-user-from-blacklist! [user-id command]
  (add-event! (-> (command->event command user-id)
                  (assoc :event/type :blacklist.event/remove))))

;; TODO: Could unify API with add-user-to-blacklist!
(defn add-users-to-blacklist! [{:keys [users actor comment] :as params}]
  (doseq [user users]
    (add-event! {:event/type :blacklist.event/add
                 :event/actor actor
                 :event/time (time/now)
                 :userid (:userid user)
                 :resource/ext-id (:resource/ext-id params)
                 :event/comment comment})))

(defn get-blacklist [params] (blacklist/get-blacklist params))
