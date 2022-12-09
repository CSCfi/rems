(ns rems.api.categories
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.schema-base :as schema-base]
            [rems.service.category :as category]
            [rems.api.util :refer [not-found-json-response]]
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
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

    (POST "/create" []
      :summary "Create category"
      :roles +admin-write-roles+
      :body [command schema/CreateCategoryCommand]
      :return CreateCategoryResponse
      (ok (category/create-category! command)))

    (PUT "/edit" []
      :summary "Update category"
      :roles +admin-write-roles+
      :body [command schema/UpdateCategoryCommand]
      :return schema/SuccessResponse
      (if (category/get-category (:category/id command))
        (ok (category/update-category! command))
        (not-found-json-response)))

    (POST "/" []
      :summary "Create category. DEPRECATED, will disappear, use /create instead"
      :roles +admin-write-roles+
      :body [command schema/CreateCategoryCommand]
      :return CreateCategoryResponse
      (ok (category/create-category! command)))

    (PUT "/" []
      :summary "Update category, DEPRECATED, will disappear, use /edit instead"
      :roles +admin-write-roles+
      :body [command schema/UpdateCategoryCommand]
      :return schema/SuccessResponse
      (if (category/get-category (:category/id command))
        (ok (category/update-category! command))
        (not-found-json-response)))

    (POST "/delete" []
      :summary "Delete category"
      :roles +admin-write-roles+
      :body [command schema/DeleteCategoryCommand]
      :return schema/SuccessResponse
      (if (category/get-category (:category/id command))
        (ok (category/delete-category! command))
        (not-found-json-response)))))
