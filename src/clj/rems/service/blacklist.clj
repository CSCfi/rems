(ns rems.service.blacklist
  (:require [clj-time.core :as time]
            [rems.db.blacklist :as blacklist]
            [rems.db.resource :as resource]
            [rems.db.users :as users]
            [rems.service.dependencies :as dependencies]))

(defn- command->event [command actor]
  {:event/actor actor
   :event/time (time/now)
   :userid (get-in command [:blacklist/user :userid])
   :resource/ext-id (get-in command [:blacklist/resource :resource/ext-id])
   :event/comment (:comment command)})

(defn- check-foreign-keys [event]
  ;; TODO: These checks could be moved to the database as (1) constraint checks or (2) fields with foreign keys.
  (when-not (users/user-exists? (:userid event))
    (throw (IllegalArgumentException. "user doesn't exist")))
  (when-not (resource/ext-id-exists? (:resource/ext-id event))
    (throw (IllegalArgumentException. "resource doesn't exist")))
  event)

(defn blacklisted? [userid resource]
  (blacklist/blacklisted? userid resource))

(defn add-event! [event]
  (blacklist/add-event! (check-foreign-keys event)))

(defn update-event! [event]
  (blacklist/update-event! (check-foreign-keys event)))

(defn add-user-to-blacklist! [actor command]
  (blacklist/add-event! (-> (command->event command actor)
                            (assoc :event/type :blacklist.event/add))))

(defn remove-user-from-blacklist! [actor command]
  (blacklist/add-event! (-> (command->event command actor)
                            (assoc :event/type :blacklist.event/remove))))

(defn- format-blacklist-entry [entry]
  {:blacklist/resource {:resource/ext-id (:resource/ext-id entry)}
   :blacklist/user (users/get-user (:userid entry))
   :blacklist/added-at (:event/time entry)
   :blacklist/comment (:event/comment entry)
   :blacklist/added-by (users/get-user (:event/actor entry))})

(defn get-blacklist [params]
  (->> (blacklist/get-blacklist params)
       (mapv format-blacklist-entry)))
