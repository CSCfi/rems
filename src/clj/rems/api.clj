(ns rems.api
  (:require [compojure.api.exception :as ex]
            [compojure.api.sweet :refer :all]
            [rems.api.actions :refer [actions-api]]
            [rems.api.application :refer [application-api]]
            [rems.api.applications :refer [applications-api]]
            [rems.api.catalogue :refer [catalogue-api]]
            [rems.api.schema :refer :all]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.form :as form]
            [rems.locales :as locales]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]
           rems.auth.NotAuthorizedException))

(def GetTranslationsResponse
  s/Any)

(def GetThemeResponse
  s/Any)

(def ExtraPage
  {s/Keyword s/Any})

(def GetConfigResponse
  {:authentication s/Keyword
   (s/optional-key :extra-pages) [ExtraPage]})

(def Entitlement
  {:resource s/Str
   :application-id s/Num
   :start s/Str
   :mail s/Str})

(defn unauthorized-handler
  [exception ex-data request]
  (unauthorized "unauthorized"))

(defn invalid-handler
  [exception ex-data request]
  (bad-request "invalid request"))

(def cors-middleware
  #(wrap-cors
    %
    :access-control-allow-origin #".*"
    :access-control-allow-methods [:get :put :post :delete]))

(def api-routes
  (api
   {;; TODO: should this be in rems.middleware?
    :middleware [cors-middleware]
    :exceptions {:handlers {rems.auth.NotAuthorizedException (ex/with-logging unauthorized-handler)
                            rems.InvalidRequestException (ex/with-logging invalid-handler)
                            ;; add logging to validation handlers
                            ::ex/request-validation (ex/with-logging ex/request-validation-handler)
                            ::ex/request-parsing (ex/with-logging ex/request-parsing-handler)
                            ::ex/response-validation (ex/with-logging ex/response-validation-handler)}}
    :swagger {:ui "/swagger-ui"
              :spec "/swagger.json"
              :data {:info {:version "1.0.0"
                            :title "REMS API"
                            :description "REMS API Services"}}}}

   (context "/api" []
     :header-params [{x-rems-api-key :- (describe s/Str "REMS API-Key (optional for UI, required for API)") nil}
                     {x-rems-user-id :- (describe s/Str "user id (optional for UI, required for API)") nil}]

     (context "/translations" []
       :tags ["translations"]

       (GET "/" []
         :summary "Get translations"
         :return GetTranslationsResponse
         (ok locales/translations)))

     (context "/theme" []
       :tags ["theme"]

       (GET "/" []
         :summary "Get current layout theme"
         :return GetThemeResponse
         (ok context/*theme*)))

     (context "/config" []
       :tags ["config"]

       (GET "/" []
         :summary "Get configuration that is relevant to UI"
         :return GetConfigResponse
         (ok (select-keys env [:authentication :extra-pages]))))

     actions-api

     application-api

     applications-api

     catalogue-api

     (context "/entitlements" []
       :tags ["entitlements"]

       (GET "/" []
         :summary "Get all entitlements"
         :query-params [{user :- (describe s/Str "return entitlements for this user (optional)") nil}
                        {resource :- (describe s/Str "return entitlements for this resource (optional)") nil}]
         :return [Entitlement]
         (ok (entitlements/get-entitlements-for-api user resource)))))))
