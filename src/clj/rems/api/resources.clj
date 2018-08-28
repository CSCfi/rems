(ns rems.api.resources
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def CreateResourceCommand
  {:resid s/Str
   :prefix s/Str
   :licenses [s/Num]})

(defn- format-resource
  [{:keys [id modifieruserid prefix resid start endt active?]}]
  {:id id
   :modifieruserid modifieruserid
   :prefix prefix
   :resid resid
   :start start
   :end endt
   :active active?})

(defn- get-resources [filters]
  (doall
   (for [res (resource/get-resources filters)]
     (assoc (format-resource res)
            :licenses (licenses/get-resource-licenses (:id res))))))

(defn- create-resource [{:keys [resid prefix licenses]}]
  (let [id (:id (db/create-resource! {:resid resid :prefix prefix :modifieruserid (get-user-id)}))]
    (doseq [licid licenses]
      (db/create-resource-license! {:resid id :licid licid}))))

(def resources-api
  (context "/resources" []
    :tags ["resources"]

    (GET "/" []
      :summary "Get resources"
      :query-params [{active :- (describe s/Bool "filter active or inactive resources") nil}]
      :return [Resource]
      (check-user)
      (check-roles :owner)
      (ok (get-resources (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create resource"
      :body [command CreateResourceCommand]
      (check-user)
      (check-roles :owner)
      (ok (create-resource command)))))
