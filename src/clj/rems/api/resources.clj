(ns rems.api.resources
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(def Resource
  {:id s/Num
   :owneruserid s/Str
   :modifieruserid s/Str
   :organization s/Str
   :resid s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :active s/Bool
   :licenses [ResourceLicense]})

(def CreateResourceCommand
  {:resid s/Str
   :organization s/Str
   :licenses [s/Num]})

(def CreateResourceResponse
  {:id s/Num})

(defn- format-resource
  [{:keys [id owneruserid modifieruserid organization resid start endt active?]}]
  {:id id
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :organization organization
   :resid resid
   :start start
   :end endt
   :active active?})

(defn- get-resources [filters]
  (doall
   (for [res (resource/get-resources filters)]
     (assoc (format-resource res)
            :licenses (licenses/get-resource-licenses (:id res))))))

(def resources-api
  (context "/resources" []
    :tags ["resources"]

    (GET "/" []
      :summary "Get resources (roles: owner)"
      :query-params [{active :- (describe s/Bool "filter active or inactive resources") nil}]
      :return [Resource]
      (check-user)
      (check-roles :owner)
      (ok (get-resources (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create resource (roles: owner)"
      :body [command CreateResourceCommand]
      :return CreateResourceResponse
      (check-user)
      (check-roles :owner)
      (ok (resource/create-resource! command)))))
