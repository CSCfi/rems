(ns rems.api.catalogue-items
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.catalogue :as catalogue]
            [rems.api.util :refer [not-found-json-response check-user]] ; required for route :roles
            [rems.common.roles :refer [+admin-write-roles+]]
            [rems.common.util :refer [apply-filters]]
            [rems.schema-base :as schema-base]
            [ring.swagger.json-schema :as rjs]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetCatalogueItemsResponse
  [schema/CatalogueItem])

(s/defschema CatalogueItemLocalization
  {:title s/Str
   ;; s/optional-key to keep backwards compatibility. s/maybe to allow unsetting the field.
   (s/optional-key :infourl) (s/maybe s/Str)})

(s/defschema WriteCatalogueItemLocalizations
  (rjs/field {schema-base/Language CatalogueItemLocalization}
             {:description "Localizations keyed by language"
              :example {:fi {:title "Title in Finnish"
                             :infourl "http://example.fi"}
                        :en {:title "Title in English"
                             :infourl "http://example.com"}}}))

;; TODO resid is misleading: it's the internal id, not the string id
;; Should we take the string id instead?
(s/defschema CreateCatalogueItemCommand
  {:form (s/maybe s/Int)
   :resid s/Int
   :wfid s/Int
   :organization schema-base/OrganizationId
   :localizations WriteCatalogueItemLocalizations
   (s/optional-key :enabled) s/Bool
   (s/optional-key :archived) s/Bool
   (s/optional-key :categories) [schema-base/CategoryId]})

(s/defschema EditCatalogueItemCommand
  {:id s/Int
   :localizations WriteCatalogueItemLocalizations
   (s/optional-key :organization) schema-base/OrganizationId
   (s/optional-key :categories) [schema-base/CategoryId]})

(s/defschema CreateCatalogueItemResponse
  {:success s/Bool
   (s/optional-key :id) s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema ChangeFormCommand
  {:form (describe (s/maybe s/Int) "new form id")})

(s/defschema ChangeFormResponse
  {:success s/Bool
   (s/optional-key :catalogue-item-id) s/Int
   (s/optional-key :errors) [s/Any]})

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
      (ok (apply-filters
           (merge (when-not expired {:expired false})
                  (when-not disabled {:enabled true})
                  (when-not archived {:archived false}))
           (catalogue/get-localized-catalogue-items {:resource resource
                                                     :expand-names? (str/includes? (or expand "") "names")
                                                     :archived archived}))))

    (POST "/:item-id/change-form" []
      :summary "Change catalogue item form. Creates a copy and ends the old."
      :roles +admin-write-roles+
      :path-params [item-id :- (describe s/Int "catalogue item")]
      :body [command ChangeFormCommand]
      :responses {200 {:schema ChangeFormResponse}
                  404 {:schema s/Any :description "Not found"}}
      (if-let [it (catalogue/get-localized-catalogue-item item-id)]
        (ok (catalogue/change-form! it (:form command)))
        (not-found-json-response)))

    (GET "/:item-id" []
      :summary "Get a single catalogue item"
      :path-params [item-id :- (describe s/Int "catalogue item")]
      :responses {200 {:schema schema/CatalogueItem}
                  404 {:schema s/Any :description "Not found"}}

      (check-user)
      (if-let [it (catalogue/get-localized-catalogue-item item-id)]
        (ok it)
        (not-found-json-response)))

    (POST "/create" []
      :summary "Create a new catalogue item"
      :roles +admin-write-roles+
      :body [command CreateCatalogueItemCommand]
      :return CreateCatalogueItemResponse
      (ok (catalogue/create-catalogue-item! command)))

    (PUT "/edit" []
      :summary "Edit a catalogue item"
      :roles +admin-write-roles+
      :body [command EditCatalogueItemCommand]
      :return schema/SuccessResponse
      (if (nil? (catalogue/get-localized-catalogue-item (:id command)))
        (not-found-json-response)
        (ok (catalogue/edit-catalogue-item! command))))

    (PUT "/archived" []
      :summary "Archive or unarchive catalogue item"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (ok (catalogue/set-catalogue-item-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable catalogue item"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (ok (catalogue/set-catalogue-item-enabled! command)))))
