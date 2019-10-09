(ns rems.api.blacklist
  (:require [clj-time.core :as time]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.db.blacklist :as blacklist]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s]))

(s/defschema BlacklistCommand
  {:command (s/enum :add :remove)
   :resource blacklist/ResourceId
   :user blacklist/UserId
   :comment s/Str})

(s/defschema BlacklistResponse
  [{:resource blacklist/ResourceId
    :user blacklist/UserId}])

(defn- command->event [command]
  {:event/type (case (:command command)
                 :add :blacklist.event/add
                 :remove :blacklist.event/remove)
   :event/actor (getx-user-id)
   :event/time (time/now)
   :blacklist/user (:user command)
   :blacklist/resource (:resource command)
   :event/comment (:comment command)})

(def blacklist-api
  (context "/blacklist" []
    :tags ["blacklist"]
    (GET "/" []
      :summary "Get blacklist entries"
      :roles #{:handler :owner}
      :query-params [{user :- blacklist/UserId nil}
                     {resource :- blacklist/ResourceId nil}]
      :return BlacklistResponse
      (ok (blacklist/get-blacklist {:blacklist/user user
                                    :blacklist/resource resource})))
    (POST "/command" []
      :summary "Add or remove a blacklist entry"
      :roles #{:owner}
      :body [command BlacklistCommand]
      :return schema/SuccessResponse
      (blacklist/add-event! (command->event command))
      (ok {:success true}))))
