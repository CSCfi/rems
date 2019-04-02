(ns rems.api.catalogue-items
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-user]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetCatalogueItemsResponse
  [CatalogueItem])

;; TODO resid is misleading: it's the internal id, not the string id
;; Should we take the string id instead?
(s/defschema CreateCatalogueItemCommand
  {:title s/Str
   :form s/Num
   :resid s/Num
   :wfid s/Num
   (s/optional-key :enabled) s/Bool
   (s/optional-key :archived) s/Bool})

(s/defschema CreateCatalogueItemResponse
  {:success s/Bool
   :id s/Num})

(s/defschema CreateCatalogueItemLocalizationCommand
  {:id s/Num
   :langcode s/Str
   :title s/Str})

;; TODO use declarative roles everywhere
(def catalogue-items-api
  (context "/catalogue-items" []
    :tags ["catalogue items"]

    (GET "/" []
      :summary "Get catalogue items"
      :roles #{:logged-in}
      :query-params [{resource :- (describe s/Str "resource id (optional)") nil}
                     {expand :- (describe s/Str "expanded additional attributes (optional), can be \"names\"") nil}
                     {archived :- (describe s/Bool "'true' to include archived items, defaults to 'false'") false}]
      :return GetCatalogueItemsResponse
      (ok (catalogue/get-localized-catalogue-items {:resource resource
                                                    :expand-names? (str/includes? (or expand "") "names")
                                                    :archived archived})))

    (GET "/:item-id" []
      :summary "Get a single catalogue item"
      :path-params [item-id :- (describe s/Num "catalogue item")]
      :responses {200 {:schema CatalogueItem}
                  404 {:schema s/Str :description "Not found"}}

      (check-user)
      (if-let [it (catalogue/get-localized-catalogue-item item-id)]
        (ok it)
        (not-found! "not found")))

    (POST "/create" []
      :summary "Create a new catalogue item"
      :roles #{:owner}
      :body [command CreateCatalogueItemCommand]
      :return CreateCatalogueItemResponse
      (ok (catalogue/create-catalogue-item! command)))

    (PUT "/update" []
      :summary "Update catalogue item"
      :roles #{:owner}
      :body [command UpdateStateCommand]
      :return SuccessResponse
      (ok (catalogue/update-catalogue-item! command)))

    (POST "/create-localization" []
      :summary "Create a new catalogue item localization"
      :roles #{:owner}
      :body [command CreateCatalogueItemLocalizationCommand]
      :return SuccessResponse
      (ok (catalogue/create-catalogue-item-localization! command)))))
