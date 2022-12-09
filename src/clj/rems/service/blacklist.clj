(ns rems.service.blacklist
  (:require [clj-time.core :as time]
            [rems.db.applications :as applications]
            [rems.db.blacklist :as blacklist]
            [rems.db.users :as users]))

(defn- command->event [command actor]
  {:event/actor actor
   :event/time (time/now)
   :userid (get-in command [:blacklist/user :userid])
   :resource/ext-id (get-in command [:blacklist/resource :resource/ext-id])
   :event/comment (:comment command)})

(defn add-user-to-blacklist! [actor command]
  (blacklist/add-event! (-> (command->event command actor)
                            (assoc :event/type :blacklist.event/add)))
  (applications/reload-cache!))

(defn remove-user-from-blacklist! [actor command]
  (blacklist/add-event! (-> (command->event command actor)
                            (assoc :event/type :blacklist.event/remove)))
  (applications/reload-cache!))

(defn- format-blacklist-entry [entry]
  {:blacklist/resource {:resource/ext-id (:resource/ext-id entry)}
   :blacklist/user (users/get-user (:userid entry))
   :blacklist/added-at (:event/time entry)
   :blacklist/comment (:event/comment entry)
   :blacklist/added-by (users/get-user (:event/actor entry))})

(defn get-blacklist [params]
  (->> (blacklist/get-blacklist params)
       (mapv format-blacklist-entry)))
