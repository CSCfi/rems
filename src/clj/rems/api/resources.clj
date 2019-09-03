(ns rems.api.resources
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.services.resource :as resource]
            [rems.api.util] ; required for route :roles
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
   :start DateTime
   :end (s/maybe DateTime)
   :expired s/Bool
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
      :roles #{:owner}
      :query-params [{disabled :- (describe s/Bool "whether to include disabled resources") false}
                     {expired :- (describe s/Bool "whether to include expired resources") false}
                     {archived :- (describe s/Bool "whether to include archived resources") false}]
      :return Resources
      (ok (resource/get-resources (merge (when-not expired {:expired false})
                                         (when-not disabled {:enabled true})
                                         (when-not archived {:archived false})))))

    (GET "/:resource-id" []
      :summary "Get resource by id"
      :roles #{:owner}
      :path-params [resource-id :- (describe s/Int "resource id")]
      :return Resource
      (ok (resource/get-resource resource-id)))

    (POST "/create" []
      :summary "Create resource"
      :roles #{:owner}
      :body [command CreateResourceCommand]
      :return CreateResourceResponse
      (ok (resource/create-resource! command (getx-user-id))))

    (PUT "/archived" []
      :summary "Archive or unarchive resource"
      :roles #{:owner}
      :body [command ArchivedCommand]
      :return SuccessResponse
      (ok (resource/set-resource-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable resource"
      :roles #{:owner}
      :body [command EnabledCommand]
      :return SuccessResponse
      (ok (resource/set-resource-enabled! command)))))
