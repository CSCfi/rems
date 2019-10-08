(ns rems.api.blacklist
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.db.blacklist :as blacklist]
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

(def blacklist-api
  (context "/blacklist" []
    :tags ["blacklist"]
    (GET "/" []
      :summary "Get blacklist entries"
      :roles #{:handler :owner}
      :query-params [{user :- blacklist/UserId false}
                     {resource :- blacklist/ResourceId false}]
      :return BlacklistResponse
      (ok []))
    (POST "/command" []
      :summary "Add or remove a blacklist entry"
      :roles #{:owner}
      :body [command BlacklistCommand]
      :return schema/SuccessResponse
      (ok {:success true}))))
