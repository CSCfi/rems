(ns rems.routes.services
  (:require [compojure.api.sweet :refer :all]
            [rems.context :as context]
            [rems.db.applications :refer [get-draft-id-for
                                          get-form-for]]
            [rems.form :as form]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(def License
  {:id Long
   :type s/Str
   :licensetype s/Str
   :title s/Str
   :textcontent s/Str
   :approved s/Bool})

(def Item
  {:id Long
   :title s/Str
   :inputprompt (s/maybe s/Str)
   :optional s/Bool
   :type s/Str
   :value (s/maybe s/Str)})

(def Event Long)

(def Application
  {:id Long
   :state s/Str
   :catid Long
   :applicantuserid s/Str
   :start DateTime
   :wfid Long
   :curround Long
   :fnlround Long
   :events [Event]})

(def GetApplicationResponse
  {:id Long
   :catalogue-item Long
   :applicant-attributes (s/maybe {s/Str s/Str})
   :application Application
   :licenses [License]
   :title s/Str
   :items [Item]})

(def ValidationError s/Str)

(def SaveApplicationRequest
  {:operation s/Str
   (s/optional-key :application-id) Long
   :items {s/Keyword s/Str}  ;; NOTE: compojure-api only supports keywords here
   (s/optional-key :licenses) {s/Keyword s/Str}  ;; NOTE: compojure-api only supports keywords here
   })

(def SaveApplicationResponse
  {:success s/Bool
   :valid s/Bool
   (s/optional-key :id) Long
   (s/optional-key :state) s/Str
   (s/optional-key :validation) [ValidationError]})

(defn longify-keys [m]
  (into {} (for [[k v] m]
             [(Long/parseLong (name k)) v])))

(defn fix-keys [application]
  (-> application
      (update-in [:items] longify-keys)
      (update-in [:licenses] longify-keys)))

(defapi service-routes
  {:swagger {:ui "/swagger-ui"
             :spec "/swagger.json"
             :data {:info {:version "1.0.0"
                           :title "Sample API"
                           :description "Sample Services"}}}}

  (context "/api" []
           :tags ["application"]

           (GET "/application/:resource-id" []
                :summary     "Get application draft by resource-id"
                :path-params [resource-id :- Long]
                :return      GetApplicationResponse
                (let [app (get-draft-id-for resource-id)]
                  (ok (get-form-for resource-id app))))

           (GET "/application/:resource-id/:application-id" []
                :summary     "Get application by resource-id and application-id"
                :path-params [resource-id :- Long, application-id :- Long]
                :return      GetApplicationResponse
                (ok (get-form-for resource-id application-id)))

           (PUT "/application/:resource-id" []
                :summary     "Put application by resource-id"
                :path-params [resource-id :- Long]
                :body        [request SaveApplicationRequest]
                :return      SaveApplicationResponse
                (ok (form/form-save resource-id (fix-keys request))))
           ))
