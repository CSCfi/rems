(ns rems.api.users
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.users :as users]
            [rems.middleware :as middleware]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateUserCommand
  ;; we can't use UserWithAttributes here since UserWithAttributes
  ;; contains :notification-email which isn't part of user
  ;; attributes (but instead comes from user settings)
  {:userid UserId
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)
   (s/optional-key :organizations) [OrganizationId]})

(def users-api
  (context "/users" []
    :tags ["users"]

    (POST "/create" []
      :summary "Create or update user"
      :roles #{:owner :user-owner}
      :body [command CreateUserCommand]
      :return SuccessResponse
      (users/add-user! command)
      (ok {:success true}))

    (GET "/active" []
      :summary "List active users"
      :roles #{:owner}
      :return [UserWithAttributes]
      (ok (middleware/get-active-users)))))
