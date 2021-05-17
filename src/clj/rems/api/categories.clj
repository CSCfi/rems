(ns rems.api.categories
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [not-found-json-response check-user]]
            [rems.schema-base :as schema-base]
            [rems.api.services.categories :as categories]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema Name {:name s/Str})

(s/defschema Category
  {:id s/Int
  ;;  :owneruserid s/Any
  ;;  :modifieruserid s/Any
   (s/optional-key :data) s/Any
   (s/optional-key :organization) s/Any})

(s/defschema GetCategoriesResponse
  [Category])

(s/defschema PostCategoriesResponse
  [Category])

(s/defschema CreateCategoryCommand
  {:id s/Int
   (s/optional-key :data) s/Any
   (s/optional-key :organization) s/Any})


(def categories-api
  (context "/categories" []
    :tags ["categories"]

    (GET "/" []
      :summary "Get categories"
      :return GetCategoriesResponse
      (ok (categories/get-categories))
      ;; (not-found-json-response)
      )

    (POST "/create" []
      :summary "Create category"
      :body [command CreateCategoryCommand]
      :return PostCategoriesResponse
      (ok (categories/create-category! command)))))