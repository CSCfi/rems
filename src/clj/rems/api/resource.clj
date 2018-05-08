(ns rems.api.resource
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util :refer [check-user check-roles]]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def ResourceLicense
  {:id s/Num
   :licensetype (s/enum "text" "link" "attachment")
   :start DateTime
   :end (s/maybe DateTime)
   :title s/Str
   :textcontent s/Str
   :localizations {s/Keyword {:title s/Str :textcontent s/Str}}})

(def Resource
  {:id s/Num
   :modifieruserid s/Str
   :resid s/Str
   :start DateTime
   :end (s/maybe DateTime)
   :licenses [ResourceLicense]})

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
