(ns rems.api.categories
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.schema-base :as schema-base]
            [rems.api.services.category :as category]
            [rems.api.util :refer [not-found-json-response]]
            [rems.common.roles :refer [+admin-read-roles+]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateCategoryResponse
  (s/if (comp not :success)
    schema/SuccessResponse
    (merge schema/SuccessResponse
           {:category/id s/Int})))

(def categories-api
  (context "/categories" []
    :tags ["categories"]

    (GET "/" []
      :summary "Get all categories"
      :roles +admin-read-roles+
      :return [schema-base/Category]
      (ok (category/get-categories)))

    (GET "/:category-id" []
      :summary "Get category by id"
      :roles +admin-read-roles+
      :path-params [category-id :- (describe s/Int "category id")]
      :return schema-base/CategoryFull
      (if-let [category (category/get-category category-id)]
        (ok category)
        (not-found-json-response)))

    (POST "/" []
      :summary "Create category"
      :roles #{:owner}
      :body [command schema/CreateCategoryCommand]
      :return CreateCategoryResponse
      (ok (category/create-category! command)))

    (PUT "/" []
      :summary "Update category"
      :roles #{:owner}
      :body [command schema/UpdateCategoryCommand]
      :return schema/SuccessResponse
      (ok (category/update-category! command)))

    (POST "/remove" []
      :summary "Delete category"
      :roles #{:owner}
      :body [command schema/DeleteCategoryCommand]
      :return schema/SuccessResponse
      (ok (category/delete-category! command)))))