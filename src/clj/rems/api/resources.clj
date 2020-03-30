(ns rems.api.resources
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.services.resource :as resource]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;; TODO convert to V2Resource
(s/defschema Resource
  {:id s/Int
   :owneruserid s/Str
   :modifieruserid s/Str
   :organization s/Str
   :resid s/Str
   :enabled s/Bool
   :archived s/Bool
   :licenses [ResourceLicense]})

(s/defschema Resources
  [Resource])

(s/defschema CreateResourceCommand
  {:resid s/Str
   :organization s/Str
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
      :roles #{:owner :organization-owner :handler}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled resources") false}
                     {archived :- (describe s/Bool "whether to include archived resources") false}]
      :return Resources
      (ok (resource/get-resources (merge (when-not disabled {:enabled true})
                                         (when-not archived {:archived false})))))

    (GET "/:resource-id" []
      :summary "Get resource by id"
      :roles #{:owner :organization-owner :handler}
      :path-params [resource-id :- (describe s/Int "resource id")]
      :return Resource
      (if-let [resource (resource/get-resource resource-id)]
        (ok resource)
        (not-found-json-response)))

    (POST "/create" []
      :summary "Create resource"
      :roles #{:owner :organization-owner}
      :body [command CreateResourceCommand]
      :return CreateResourceResponse
      (ok (resource/create-resource! command (getx-user-id))))

    (PUT "/archived" []
      :summary "Archive or unarchive resource"
      :roles #{:owner :organization-owner}
      :body [command ArchivedCommand]
      :return SuccessResponse
      (ok (resource/set-resource-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable resource"
      :roles #{:owner :organization-owner}
      :body [command EnabledCommand]
      :return SuccessResponse
      (ok (resource/set-resource-enabled! command)))))
