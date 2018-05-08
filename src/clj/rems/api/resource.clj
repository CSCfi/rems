(ns rems.api.resource
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :refer :all]
            [rems.api.util :refer [check-user check-roles]]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(defn- format-resource
  [{:keys [id modifieruserid resid start endt]}]
  {:id id
   :modifieruserid modifieruserid
   :resid resid
   :start start
   :end endt})

(defn- get-resources []
  (doall
   (for [res (db/get-resources)]
     (assoc (format-resource res)
            :licenses (licenses/get-resource-licenses (:id res))))))

(def resource-api
  (context "/resource" []
    :tags ["resource"]

    (GET "/" []
      :summary "Get resources"
      :return [Resource]
      (check-user)
      (check-roles :approver) ;; TODO admin role needed?
      (ok (get-resources)))))
