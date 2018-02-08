(ns rems.routes.services
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.exception :as ex]
            [rems.context :as context]
            [rems.db.applications :refer [get-draft-form-for
                                          get-form-for
                                          make-draft-application]]
            [rems.db.catalogue :as catalogue]
            [rems.form :as form]
            [rems.locales :as locales]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]
           rems.auth.NotAuthorizedException))

(def License
  {:id s/Num
   :type s/Str
   :licensetype s/Str
   :title s/Str
   :textcontent s/Str
   :approved s/Bool})

(def Item
  {:id s/Num
   :title s/Str
   :inputprompt (s/maybe s/Str)
   :optional s/Bool
   :type s/Str
   :value (s/maybe s/Str)})

(def Event
  {:userid s/Str
   :round s/Num
   :event s/Str
   :comment (s/maybe s/Str)
   :time DateTime})

(def Application
  {:id s/Num
   :state s/Str
   :applicantuserid s/Str
   :start DateTime
   :wfid s/Num
   :curround s/Num
   :fnlround s/Num
   :events [Event]
   :can-approve? s/Bool
   :can-close? s/Bool
   :review-type (s/maybe s/Keyword)})

(def CatalogueItem
  {:id s/Num
   :title s/Str
   :wfid s/Num
   :formid s/Num
   :resid s/Str
   :state s/Str
   (s/optional-key :langcode) s/Keyword
   :localizations (s/maybe {s/Any s/Any})
   })

(def GetTranslationsResponse
  s/Any)

(def GetApplicationResponse
  {:id s/Num
   :catalogue-items [CatalogueItem]
   :applicant-attributes (s/maybe {s/Str s/Str})
   :application (s/maybe Application)
   :licenses [License]
   :title s/Str
   :items [Item]})

(def ValidationError s/Str)

(def SaveApplicationRequest
  {:operation s/Str
   (s/optional-key :application-id) s/Num
   (s/optional-key :catalogue-items) [s/Num]
   :items {s/Keyword s/Str}  ;; NOTE: compojure-api only supports keywords here
   (s/optional-key :licenses) {s/Keyword s/Str}  ;; NOTE: compojure-api only supports keywords here
   })

(def SaveApplicationResponse
  {:success s/Bool
   :valid s/Bool
   (s/optional-key :id) s/Num
   (s/optional-key :state) s/Str
   (s/optional-key :validation) [ValidationError]})

(def GetCatalogueResponse
  [CatalogueItem])

(defn unauthorized-handler
  [exception ex-data request]
  (unauthorized "unauthorized"))

(def service-routes
  (api
   {:exceptions {:handlers {rems.auth.NotAuthorizedException (ex/with-logging unauthorized-handler)
                            ;; add logging to validation handlers
                            ::ex/request-validation (ex/with-logging ex/request-validation-handler)
                            ::ex/request-parsing (ex/with-logging ex/request-parsing-handler)
                            ::ex/response-validation (ex/with-logging ex/response-validation-handler)}}
    :swagger {:ui "/swagger-ui"
              :spec "/swagger.json"
              :data {:info {:version "1.0.0"
                            :title "Sample API"
                            :description "Sample Services"}}}}

   (context "/api" []
            :tags ["translation"]

            (GET "/translations" []
                 :summary     "Get translations"
                 :return      GetTranslationsResponse
                 (ok locales/translations)))

   (context "/api" []
            :tags ["application"]

            (GET "/application/" []
                 :summary     "Get application draft by `catalogue-items`"
                 :query-params [catalogue-items :- Long]
                 :return      GetApplicationResponse
                 (let [app (make-draft-application -1 catalogue-items)]
                   (ok (get-draft-form-for app))))

            (GET "/application/:application-id" []
                 :summary     "Get application by `application-id`"
                 :path-params [application-id :- Long]
                 :return      GetApplicationResponse
                 (binding [context/*lang* :en]
                   (ok (get-form-for application-id))))

            (PUT "/application" []
                 :summary     "Create a new application or change an existing one"
                 :body        [request SaveApplicationRequest]
                 :return      SaveApplicationResponse
                 (ok (form/api-save request))))

   (context "/api" []
            :tags ["catalogue"]

            (GET "/catalogue/" []
                 :summary "Get catalogue items"
                 :return GetCatalogueResponse
                 (binding [context/*lang* :en]
                   (ok (catalogue/get-localized-catalogue-items)))))))
