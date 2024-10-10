(ns rems.api.catalogue-items
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [medley.core :refer [assoc-some]]
            [rems.api.schema :as schema]
            [rems.service.catalogue]
            [rems.api.util :refer [not-found-json-response check-user extended-logging]] ; required for route :roles
            [rems.common.roles :refer [+admin-write-roles+]]
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
  {(s/optional-key :form) (s/maybe s/Int)
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

(s/defschema UpdateCatalogueItemCommand
  {(s/optional-key :form) (describe (s/maybe s/Int) "new form id")
   (s/optional-key :workflow) (describe (s/maybe s/Int) "new workflow id")})

(s/defschema UpdateCatalogueItemResponse
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
      (ok (rems.service.catalogue/get-catalogue-items
           ;; nil = dont filter this attribute
           (assoc-some {:archived (when-not archived false)
                        :enabled (when-not disabled true)
                        :expired (when-not expired false)}
                       :expand-names? (when expand (str/includes? expand "names"))
                       :resource resource))))

    (POST "/:item-id/change-form" request
      :summary "Change catalogue item form. Creates a copy and ends the old. DEPRECATED, will disappear, use /update instead"
      :roles +admin-write-roles+
      :path-params [item-id :- (describe s/Int "catalogue item")]
      :body [command ChangeFormCommand]
      :responses {200 {:schema ChangeFormResponse}
                  404 {:schema s/Any :description "Not found"}}
      (extended-logging request)
      (if-let [it (rems.service.catalogue/get-catalogue-item item-id)]
        (ok (rems.service.catalogue/change-form! it (:form command)))
        (not-found-json-response)))

    (POST "/:item-id/update" request
      :summary "Update a catalogue item allowing to change form and workflow. Creates a copy and ends the old."
      :roles +admin-write-roles+
      :path-params [item-id :- (describe s/Int "catalogue item")]
      :body [command UpdateCatalogueItemCommand]
      :responses {200 {:schema UpdateCatalogueItemResponse}
                  404 {:schema s/Any :description "Not found"}}
      (extended-logging request)
      (if-let [it (rems.service.catalogue/get-catalogue-item item-id)]
        (ok (rems.service.catalogue/update! it
                                            (merge (when (contains? command :form)
                                                     {:form-id (:form command)})
                                                   (when (contains? command :workflow)
                                                     {:workflow-id (:workflow command)}))))
        (not-found-json-response)))

    (GET "/:item-id" []
      :summary "Get a single catalogue item"
      :path-params [item-id :- (describe s/Int "catalogue item")]
      :responses {200 {:schema schema/CatalogueItem}
                  404 {:schema s/Any :description "Not found"}}
      (check-user)
      ;; XXX: deprecate :expand-names? parameter
      (if-let [it (rems.service.catalogue/get-catalogue-item item-id {:expand-names? true})]
        (ok it)
        (not-found-json-response)))

    (POST "/create" request
      :summary "Create a new catalogue item"
      :roles +admin-write-roles+
      :body [command CreateCatalogueItemCommand]
      :return CreateCatalogueItemResponse
      (extended-logging request)
      (ok (rems.service.catalogue/create-catalogue-item! command)))

    (PUT "/edit" request
      :summary "Edit a catalogue item"
      :roles +admin-write-roles+
      :body [command EditCatalogueItemCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (if (nil? (rems.service.catalogue/get-catalogue-item (:id command)))
        (not-found-json-response)
        (ok (rems.service.catalogue/edit-catalogue-item! command))))

    (PUT "/archived" request
      :summary "Archive or unarchive catalogue item"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (rems.service.catalogue/set-catalogue-item-archived! command)))

    (PUT "/enabled" request
      :summary "Enable or disable catalogue item"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (rems.service.catalogue/set-catalogue-item-enabled! command)))))
