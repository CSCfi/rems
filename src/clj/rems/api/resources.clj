(ns rems.api.resources
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.services.resource :as resource]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

;; TODO convert to V2Resource
(s/defschema Resource
  {:id s/Int
   :owneruserid s/Str
   :modifieruserid s/Str
   :organization schema/OrganizationOverview
   :resid s/Str
   :enabled s/Bool
   :archived s/Bool
   :licenses [schema/ResourceLicense]})

(s/defschema Resources
  [Resource])

(s/defschema CreateResourceCommand
  {:resid s/Str
   :organization schema/OrganizationId
   :licenses [s/Int]})

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
                     {archived :- (describe s/Bool "whether to include archived resources") false}]
      :return Resources
      (ok (resource/get-resources (merge (when-not disabled {:enabled true})
                                         (when-not archived {:archived false})))))

    (GET "/:resource-id" []
      :summary "Get resource by id"
      :roles +admin-read-roles+
      :path-params [resource-id :- (describe s/Int "resource id")]
      :return Resource
      (if-let [resource (resource/get-resource resource-id)]
        (ok resource)
        (not-found-json-response)))

    (POST "/create" []
      :summary "Create resource"
      :roles +admin-write-roles+
      :body [command CreateResourceCommand]
      :return CreateResourceResponse
      (ok (resource/create-resource! command (getx-user-id))))

    (PUT "/archived" []
      :summary "Archive or unarchive resource"
      :roles +admin-write-roles+
      :body [command schema/ArchivedCommand]
      :return schema/SuccessResponse
      (ok (resource/set-resource-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable resource"
      :roles +admin-write-roles+
      :body [command schema/EnabledCommand]
      :return schema/SuccessResponse
      (ok (resource/set-resource-enabled! command)))))
