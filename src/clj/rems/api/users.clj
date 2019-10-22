(ns rems.api.users
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.users :as users]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateUserCommand
  UserWithAttributes)

(defn create-user [user-data]
  (users/add-user! (:userid user-data) (users/unformat-user user-data)))

(def users-api
  (context "/users" []
    :tags ["users"]

    (POST "/create" []
      :summary "Create user"
      :roles #{:owner}
      :body [command CreateUserCommand]
      :return SuccessResponse
      (create-user command)
      (ok {:success true}))))
