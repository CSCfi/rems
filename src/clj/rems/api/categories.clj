(ns rems.api.categories
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [not-found-json-response check-user]]
            [rems.api.services.categories :as categories]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))


(s/defschema Category
  {:id s/Int})

(s/defschema GetCategoriesResponse
  [Category])

(s/defschema CreateCategoryCommand
  {:resid s/Str
   :data s/Str})


(def categories-api
  (context "/categories" []
    :tags ["categories"]

    (GET "/" []
      :summary "Get categories"
      :return GetCategoriesResponse
      (ok (categories/get-categories))
      (not-found-json-response))

    (POST "/create" []
      :summary "Create category"
      :body [command CreateCategoryCommand]
      :return CreateCategoryCommand
      (ok (categories/create-category! command)))))