(ns rems.api.resource
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-user check-roles]]
            [rems.db.core :as db]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def Resource
  {:id s/Num
   :modifieruserid s/Str
   :resid s/Str
   :start DateTime
   :end (s/maybe DateTime)})

(defn- format-resource
  [{:keys [id modifieruserid resid start endt]}]
  {:id id
   :modifieruserid modifieruserid
   :resid resid
   :start start
   :end endt})

(defn- get-resources []
  (mapv format-resource (db/get-resources)))

(def resource-api
  (context "/resource" []
    :tags ["resource"]

    (GET "/" []
      :summary "Get resources"
      :return [Resource]
      (check-user)
      (check-roles :approver) ;; TODO admin role needed?
      (ok (get-resources)))))
