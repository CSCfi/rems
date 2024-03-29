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
  (let [event (-> (command->event command actor)
                  (assoc :event/type :blacklist.event/add))]
    (blacklist/add-event! event)
    (applications/empty-injection-cache! :blacklisted?)
    (applications/reload-applications! {:by-userids [(get-in command [:blacklist/user :userid])]})))

(defn remove-user-from-blacklist! [actor command]
  (let [event (-> (command->event command actor)
                  (assoc :event/type :blacklist.event/remove))]
    (blacklist/add-event! event)
    (applications/empty-injection-cache! :blacklisted?)
    (applications/reload-applications! {:by-userids [(get-in command [:blacklist/user :userid])]})))

(defn- format-blacklist-entry [entry]
  {:blacklist/resource {:resource/ext-id (:resource/ext-id entry)}
   :blacklist/user (users/get-user (:userid entry))
   :blacklist/added-at (:event/time entry)
   :blacklist/comment (:event/comment entry)
   :blacklist/added-by (users/get-user (:event/actor entry))})

(defn get-blacklist [params]
  (->> (blacklist/get-blacklist params)
       (mapv format-blacklist-entry)))
