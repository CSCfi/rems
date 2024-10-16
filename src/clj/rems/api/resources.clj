(ns rems.api.resources
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.resource]
            [rems.api.util :refer [extended-logging not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.ext.duo :as duo]
            [rems.ext.mondo :as mondo]
            [rems.schema-base :as schema-base]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

;; TODO convert to V2Resource
(s/defschema Resource
  {:id s/Int
   :organization schema-base/OrganizationOverview
   :resid s/Str
   :enabled s/Bool
   :archived s/Bool
   :licenses [schema/ResourceLicense]
   (s/optional-key :resource/duo) {(s/optional-key :duo/codes) [schema-base/DuoCodeFull]}})

(s/defschema Resources
  [Resource])

(s/defschema CreateResourceCommand
  {:resid s/Str
   :organization schema-base/OrganizationId
   :licenses [s/Int]
   (s/optional-key :resource/duo) {(s/optional-key :duo/codes) [schema-base/DuoCode]}})

(s/defschema CreateResourceResponse
  {:success s/Bool
   (s/optional-key :id) s/Int
   (s/optional-key :errors) [s/Any]})

(def resources-api
  (context "/resources" []
    :tags ["resources"]

    (GET "/" []
      :summary "Get resources"
      :roles +admin-read-roles+
      :query-params [{disabled :- (describe s/Bool "whether to include disabled resources") false}
                     {archived :- (describe s/Bool "whether to include archived resources") false}
                     {resid :- (describe s/Str "optionally filter by resid (external resource identifier)") nil}]
      :return Resources
      (ok (rems.service.resource/get-resources (merge (when-not disabled {:enabled true})
                                                      (when-not archived {:archived false})
                                                      (when resid {:resid resid})))))

    (GET "/duo-codes" []
      :summary "Get DUO codes"
      :roles #{:logged-in}
      :return [schema-base/DuoCodeFull]
      (ok (duo/get-duo-codes)))

    (GET "/mondo-codes" []
      :summary "Get Mondo codes"
      :roles #{:logged-in}
      :return [schema-base/MondoCodeFull]
      (ok (mondo/get-mondo-codes)))

    (GET "/search-mondo-codes" []
      :summary "Search Mondo codes, maximum 100 results"
      :roles #{:logged-in}
      :query-params [{search-text :- (describe s/Str "text to be contained in id or label of the code") nil}]
      :return [schema-base/MondoCodeFull]
      (ok (mondo/search-mondo-codes search-text)))

    (GET "/:resource-id" []
      :summary "Get resource by id"
      :roles +admin-read-roles+
      :path-params [resource-id :- (describe s/Int "resource id")]
      :return Resource
      (if-let [resource (rems.service.resource/get-resource resource-id)]
        (ok resource)
        (not-found-json-response)))

    (POST "/create" request
      :summary "Create resource"
      :roles +admin-write-roles+
      :body [command CreateResourceCommand]
      :return CreateResourceResponse
      (extended-logging request)
      (ok (rems.service.resource/create-resource! command)))

    (PUT "/archived" request
      :summary "Archive or unarchive resource"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (rems.service.resource/set-resource-archived! command)))

    (PUT "/enabled" request
      :summary "Enable or disable resource"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (extended-logging request)
      (ok (rems.service.resource/set-resource-enabled! command)))))
