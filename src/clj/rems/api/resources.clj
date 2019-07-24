(ns rems.api.resources
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
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

(defn- format-resource
  [{:keys [id owneruserid modifieruserid organization resid start end expired enabled archived]}]
  {:id id
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :organization organization
   :resid resid
   :start start
   :end end
   :expired expired
   :enabled enabled
   :archived archived})

(defn- get-resources [filters]
  (doall
   (for [res (resource/get-resources filters)]
     (assoc (format-resource res)
            :licenses (licenses/get-resource-licenses (:id res))))))

(defn- get-resource [id]
  (-> id
      resource/get-resource
      format-resource
      (assoc :licenses (licenses/get-resource-licenses id))))

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
      (ok (get-resources (merge (when-not expired {:expired false})
                                (when-not disabled {:enabled true})
                                (when-not archived {:archived false})))))

    (GET "/:resource-id" []
      :summary "Get resource by id"
      :roles #{:owner}
      :path-params [resource-id :- (describe s/Int "resource id")]
      :return Resource
      (ok (get-resource resource-id)))

    (POST "/create" []
      :summary "Create resource"
      :roles #{:owner}
      :body [command CreateResourceCommand]
      :return CreateResourceResponse
      (ok (resource/create-resource! command (getx-user-id))))

    (PUT "/update" []
      :summary "Update resource"
      :roles #{:owner}
      :body [command UpdateStateCommand]
      :return SuccessResponse
      (ok (resource/update-resource! command)))))
