(ns rems.api.services.blacklist
  (:require [clj-time.core :as time]
            [rems.application.events :as events]
            [rems.db.blacklist :as blacklist]
            [rems.json :as json]
            [schema.core :as s]))

(defn- command->event [command actor]
  {:event/actor actor
   :event/time (time/now)
   :userid (get-in command [:blacklist/user :userid])
   :resource/ext-id (get-in command [:blacklist/resource :resource/ext-id])
   :event/comment (:comment command)})

(defn add-user-to-blacklist! [actor command]
  (blacklist/add-event! (-> (command->event command actor)
                            (assoc :event/type :blacklist.event/add))))

(defn remove-user-from-blacklist! [actor command]
  (blacklist/add-event! (-> (command->event command actor)
                            (assoc :event/type :blacklist.event/remove))))

;; TODO: Could unify API with add-user-to-blacklist!
(defn add-users-to-blacklist! [{:keys [users actor comment] :as params}]
  (doseq [user users]
    (blacklist/add-event! {:event/type :blacklist.event/add
                           :event/actor actor
                           :event/time (time/now)
                           :userid (:userid user)
                           :resource/ext-id (:resource/ext-id params)
                           :event/comment comment})))

(defn get-blacklist [params] (blacklist/get-blacklist params))
