(ns rems.api.blacklist
  (:require [clj-time.core :as time]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.db.blacklist :as blacklist]
            [rems.db.users :as users]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s]))

(s/defschema BlacklistCommand
  {:blacklist/resource {:resource/ext-id blacklist/ResourceId}
   :blacklist/user {:userid blacklist/UserId}
   :comment s/Str})

(defn- command->event [command]
  {:event/actor (getx-user-id)
   :event/time (time/now)
   :userid (get-in command [:blacklist/user :userid])
   :resource/ext-id (get-in command [:blacklist/resource :resource/ext-id])
   :event/comment (:comment command)})

(defn- format-blacklist-entry [entry]
  {:blacklist/resource {:resource/ext-id (:resource/ext-id entry)}
   :blacklist/user (users/get-user (:userid entry))})

(def blacklist-api
  (context "/blacklist" []
    :tags ["blacklist"]
    (GET "/" []
      :summary "Get blacklist entries"
      :roles #{:handler :owner :reporter}
      :query-params [{user :- blacklist/UserId nil}
                     {resource :- blacklist/ResourceId nil}]
      :return schema/Blacklist
      (->> (blacklist/get-blacklist {:userid user
                                     :resource/ext-id resource})
           (mapv format-blacklist-entry)
           (ok)))
    (POST "/add" []
      :summary "Add a blacklist entry"
      :roles #{:owner :handler}
      :body [command BlacklistCommand]
      :return schema/SuccessResponse
      (blacklist/add-event! (assoc (command->event command)
                                   :event/type :blacklist.event/add))
      (ok {:success true}))

    (POST "/remove" []
      :summary "Remove a blacklist entry"
      :roles #{:owner :handler}
      :body [command BlacklistCommand]
      :return schema/SuccessResponse
      (blacklist/add-event! (assoc (command->event command)
                                   :event/type :blacklist.event/remove))
      (ok {:success true}))))
