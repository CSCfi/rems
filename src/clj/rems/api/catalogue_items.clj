(ns rems.api.catalogue-items
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.util :refer [not-found-json-response check-user]] ; required for route :roles
            [rems.db.core :as db]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetCatalogueItemsResponse
  [CatalogueItem])

;; TODO resid is misleading: it's the internal id, not the string id
;; Should we take the string id instead?
(s/defschema CreateCatalogueItemCommand
  {:form s/Int
   :resid s/Int
   :wfid s/Int
   :localizations {s/Keyword {:title s/Str}}
   (s/optional-key :enabled) s/Bool
   (s/optional-key :archived) s/Bool})

(s/defschema EditCatalogueItemCommand
  {:id s/Int
   :localizations {s/Keyword {:title s/Str}}})

(s/defschema CreateCatalogueItemResponse
  {:success s/Bool
   :id s/Int})

;; TODO use declarative roles everywhere
(def catalogue-items-api
  (context "/catalogue-items" []
    :tags ["catalogue items"]

    (GET "/" []
      :summary "Get catalogue items"
      :roles #{:logged-in}
      :query-params [{resource :- (describe s/Str "resource id (optional)") nil}
                     {expand :- (describe s/Str "expanded additional attributes (optional), can be \"names\"") nil}
                     {archived :- (describe s/Bool "whether to include archived items") false}
                     {disabled :- (describe s/Bool "whether to include disabled items") false}
                     {expired :- (describe s/Bool "whether to include expired items") false}]
      :return GetCatalogueItemsResponse
      (ok (db/apply-filters
           (merge (when-not expired {:expired false})
                  (when-not disabled {:enabled true})
                  (when-not archived {:archived false}))
           (catalogue/get-localized-catalogue-items {:resource resource
                                                     :expand-names? (str/includes? (or expand "") "names")
                                                     :archived archived}))))

    (GET "/:item-id" []
      :summary "Get a single catalogue item"
      :path-params [item-id :- (describe s/Int "catalogue item")]
      :responses {200 {:schema CatalogueItem}
                  404 {:schema s/Any :description "Not found"}}

      (check-user)
      (if-let [it (catalogue/get-localized-catalogue-item item-id)]
        (ok it)
        (not-found-json-response)))

    (POST "/create" []
      :summary "Create a new catalogue item"
      :roles #{:owner}
      :body [command CreateCatalogueItemCommand]
      :return CreateCatalogueItemResponse
      (ok (catalogue/create-catalogue-item! command)))

    (PUT "/edit" []
      :summary "Edit a catalogue item"
      :roles #{:owner}
      :body [command EditCatalogueItemCommand]
      :return SuccessResponse
      (ok (catalogue/edit-catalogue-item! command)))

    (PUT "/update" []
      :summary "Update catalogue item"
      :roles #{:owner}
      :body [command UpdateStateCommand]
      :return SuccessResponse
      (ok (catalogue/update-catalogue-item! command)))))
