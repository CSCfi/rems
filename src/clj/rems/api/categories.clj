(ns rems.api.categories
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.schema-base :as schema-base]
            [rems.service.category]
            [rems.api.util :refer [not-found-json-response extended-logging]]
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
      (ok (rems.service.category/get-categories)))

    (GET "/:category-id" []
      :summary "Get category by id"
      :roles +admin-read-roles+
      :path-params [category-id :- (describe s/Int "category id")]
      :return schema-base/CategoryFull
      (if-let [category (rems.service.category/get-category category-id)]
        (ok category)
        (not-found-json-response)))

    (POST "/create" request
      :summary "Create category"
      :roles +admin-write-roles+
      :body [command schema/CreateCategoryCommand]
      :return CreateCategoryResponse
      (extended-logging request)
      (ok (rems.service.category/create-category! command)))

    (PUT "/edit" request
      :summary "Update category"
      :roles +admin-write-roles+
      :body [command schema/UpdateCategoryCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (if (rems.service.category/get-category (:category/id command))
        (ok (rems.service.category/update-category! command))
        (not-found-json-response)))

    (POST "/" request
      :summary "Create category. DEPRECATED, will disappear, use /create instead"
      :roles +admin-write-roles+
      :body [command schema/CreateCategoryCommand]
      :return CreateCategoryResponse
      (extended-logging request)
      (ok (rems.service.category/create-category! command)))

    (PUT "/" request
      :summary "Update category, DEPRECATED, will disappear, use /edit instead"
      :roles +admin-write-roles+
      :body [command schema/UpdateCategoryCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (if (rems.service.category/get-category (:category/id command))
        (ok (rems.service.category/update-category! command))
        (not-found-json-response)))

    (POST "/delete" request
      :summary "Delete category"
      :roles +admin-write-roles+
      :body [command schema/DeleteCategoryCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (if (rems.service.category/get-category (:category/id command))
        (ok (rems.service.category/delete-category! command))
        (not-found-json-response)))))
