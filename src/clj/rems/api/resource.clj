(ns rems.api.resource
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-user check-roles]]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def CreateResourceCommand
  {:resid s/Str
   :prefix s/Str
   :licenses [s/Num]})

(defn- format-resource
  [{:keys [id modifieruserid prefix resid start endt]}]
  {:id id
   :modifieruserid modifieruserid
   :prefix prefix
   :resid resid
   :start start
   :end endt})

(defn- get-resources []
  (doall
   (for [res (db/get-resources)]
     (assoc (format-resource res)
            :licenses (licenses/get-resource-licenses (:id res))))))

(defn- create-resource [{:keys [resid prefix licenses]}]
  (let [id (:id (db/create-resource! {:resid resid :prefix prefix :modifieruserid (get-user-id)}))]
    (doseq [licid licenses]
      (db/create-resource-license! {:resid id :licid licid}))))

(def resource-api
  (context "/resource" []
    :tags ["resource"]

    (GET "/" []
      :summary "Get resources"
      :return [Resource]
      (check-user)
      (check-roles :owner) ;; TODO admin role needed?
      (ok (get-resources)))

    (PUT "/create" []
      :summary "Create resource"
      :body [command CreateResourceCommand]
      (check-user)
      (check-roles :owner)
      (ok (create-resource command)))))
