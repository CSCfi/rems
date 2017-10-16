(ns rems.routes.services
  (:require [compojure.api.sweet :refer :all]
            [rems.db.applications :refer [get-draft-form-for
                                          get-form-for
                                          make-draft-application]]
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
   :applicantuserid s/Str
   :start DateTime
   :wfid Long
   :curround Long
   :fnlround Long
   :events [Event]})

(def CatalogueItem
  {:id Long
   :catid Long
   :langcode s/Keyword
   :title s/Str
   :wfid Long
   :resid s/Str
   :localizations {s/Any s/Any}
   })

(def GetApplicationResponse
  {:id Long
   :catalogue-items [CatalogueItem]
   :applicant-attributes (s/maybe {s/Str s/Str})
   :application Application
   :licenses [License]
   :title s/Str
   :items [Item]})

(def ValidationError s/Str)

(def SaveApplicationRequest
  {:operation s/Str
   (s/optional-key :application-id) Long
   (s/optional-key :catalogue-items) [Long]
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

           (GET "/application/" []
                :summary     "Get application draft by `catalogue-items`"
                :query-params [catalogue-items :- Long]
                :return      GetApplicationResponse
                (let [app (make-draft-application -1 catalogue-items)
                      wfid (:wfid app)]
                  (ok (get-draft-form-for app))))

           (GET "/application/:application-id" []
                :summary     "Get application by `application-id`"
                :path-params [application-id :- Long]
                :return      GetApplicationResponse
                (ok (get-form-for application-id)))

           (PUT "/application" []
                :summary     "Create a new application or change an existing one"
                :body        [request SaveApplicationRequest]
                :return      SaveApplicationResponse
                (ok (form/api-save (fix-keys request))))
           ))
