(ns rems.api.users
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.users :as users]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateUserCommand
  ;; this is not just UserWithAttributes since that contains e.g. :notification-email
  {:userid UserId
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)
   (s/optional-key :organization) (s/maybe s/Str)})

(defn create-user [user-data]
  (users/add-user! (:userid user-data) (users/unformat-user user-data)))

(def users-api
  (context "/users" []
    :tags ["users"]

    (POST "/create" []
      :summary "Create or update user"
      :roles #{:owner :user-owner}
      :body [command CreateUserCommand]
      :return SuccessResponse
      (create-user command)
      (ok {:success true}))))
