(ns rems.api.categories
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [not-found-json-response check-user]]
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [rems.api.services.categories :as categories]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema Name {:name s/Str})

(s/defschema Category
  {(s/optional-key :id) s/Int
  ;;  :owneruserid s/Any
  ;;  :modifieruserid s/Any
   (s/optional-key :data) s/Any
   (s/optional-key :organization) schema-base/OrganizationId})

(s/defschema GetCategoriesResponse
  [Category])

(s/defschema PostCategoriesResponse
  {(s/optional-key :status) s/Int
   (s/optional-key  :success) s/Any
   (s/optional-key :id) s/Int
  ;;  (s/optional-key :headers) s/Any
  ;;  (s/optional-key :body) {(s/optional-key :sucess) s/Bool
  ;;                          (s/optional-key :id) s/Int}
  ;;  
  ;;  (s/optional-key :errors) s/Any
   })

(s/defschema CreateCategoryCommand
  {(s/optional-key :id) s/Int
   (s/optional-key :data) s/Any
   (s/optional-key :organization) s/Any})

(s/defschema CreateCategoryResponse
  {(s/optional-key :id) s/Int
  ;;  :owneruserid s/Any
  ;;  :modifieruserid s/Any
   (s/optional-key :data) s/Any
   (s/optional-key :organization) schema-base/OrganizationOverview})

;; (s/defschema CreateResourceResponse
;;   {:success s/Bool
;;    (s/optional-key :id) s/Int
;;    (s/optional-key :errors) [s/Any]})


(def categories-api
  (context "/categories" []
    :tags ["categories"]

    (GET "/" []
      :summary "Get categories"
      :return GetCategoriesResponse
      (ok (categories/get-categories))
      ;; (not-found-json-response)
      )
    (GET "/:id" []
      :summary "Get resource by id"
      ;; :roles +admin-read-roles+
      :path-params [id :- (describe s/Int "category id")]
      :return CreateCategoryResponse
      (if-let [category (categories/get-category id)]
        (ok category)
        (not-found-json-response)))
    (POST "/create" []
      :summary "Create category"
      ;; :roles +admin-write-roles+
      :body [command CreateCategoryCommand]
      :return PostCategoriesResponse
      (ok (categories/create-category! command)))
    (PUT "/edit" []
      :summary "Edit workflow title and handlers"
      ;; :roles +admin-write-roles+
      ;; :path-params [id :- (describe s/Int "category id")]
      :body [command CreateCategoryCommand]
      :return PostCategoriesResponse
      (ok (categories/edit-category command)))))