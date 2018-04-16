(ns rems.api.catalogue
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.auth.util :refer [throw-unauthorized]]
            [rems.context :as context]
            [rems.db.catalogue :as catalogue]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def GetCatalogueResponse
  [CatalogueItem])

(def CreateCatalogueItemCommand
  {:title s/Str
   :form s/Num
   :resid s/Num
   :wfid s/Num})

(def CreateCatalogueItemResponse
  CatalogueItem)

(def CreateCatalogueItemLocalizationCommand
  {:id s/Num
   :langcode s/Str
   :title s/Str})

(def CreateCatalogueItemLocalizationResponse
  {:success s/Bool})

(def catalogue-api
  (context "/catalogue" []
    :tags ["catalogue"]

    (GET "/" []
      :summary "Get catalogue items"
      :query-params [{resource :- (describe s/Str "resource id") nil}]
      :return GetCatalogueResponse
      (binding [context/*lang* :en]
        (let [user-id (get-user-id)]
          (when-not user-id (throw-unauthorized))
          (ok (catalogue/get-localized-catalogue-items {:resource resource})))))

    (GET "/:item-id" []
      :summary "Get a single catalogue item"
      :path-params [item-id :- (describe s/Num "catalogue item")]
      :responses {200 {:schema CatalogueItem}
                  404 {:schema s/Str :description "Not found"}}

      (binding [context/*lang* :en]
        (if-let [it (catalogue/get-localized-catalogue-item item-id)]
          (ok it)
          (not-found! "not found"))))

    (PUT "/create" []
      :summary "Create a new catalogue item"
      :body [command CreateCatalogueItemCommand]
      :return CreateCatalogueItemResponse
      (ok (catalogue/create-catalogue-item-command! command)))

    (PUT "/create-localization" []
      :summary "Create a new catalogue item localization"
      :body [command CreateCatalogueItemLocalizationCommand]
      :return CreateCatalogueItemLocalizationResponse
      (ok (catalogue/create-catalogue-item-localization-command! command)))))
