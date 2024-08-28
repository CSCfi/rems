(ns rems.service.blacklist
  (:require [clj-time.core :as time]
            [rems.db.applications]
            [rems.db.blacklist]
            [rems.db.users]))

(defn- command->event [command actor]
  {:event/actor actor
   :event/time (time/now)
   :userid (get-in command [:blacklist/user :userid])
   :resource/ext-id (get-in command [:blacklist/resource :resource/ext-id])
   :event/comment (:comment command)})

(defn add-user-to-blacklist! [actor command]
  (let [event (-> (command->event command actor)
                  (assoc :event/type :blacklist.event/add))]
    (rems.db.blacklist/add-event! event)
    (rems.db.applications/empty-injection-cache! :blacklisted?)
    (rems.db.applications/reload-applications! {:by-userids [(get-in command [:blacklist/user :userid])]})))

(defn remove-user-from-blacklist! [actor command]
  (let [event (-> (command->event command actor)
                  (assoc :event/type :blacklist.event/remove))]
    (rems.db.blacklist/add-event! event)
    (rems.db.applications/empty-injection-cache! :blacklisted?)
    (rems.db.applications/reload-applications! {:by-userids [(get-in command [:blacklist/user :userid])]})))

(defn- format-blacklist-entry [entry]
  {:blacklist/resource {:resource/ext-id (:resource/ext-id entry)}
   :blacklist/user (rems.db.users/get-user (:userid entry))
   :blacklist/added-at (:event/time entry)
   :blacklist/comment (:event/comment entry)
   :blacklist/added-by (rems.db.users/get-user (:event/actor entry))})

(defn get-blacklist [params]
  (->> (rems.db.blacklist/get-blacklist params)
       (mapv format-blacklist-entry)))
