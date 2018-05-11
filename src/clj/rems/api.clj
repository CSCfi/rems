(ns rems.api
  (:require [compojure.api.exception :as ex]
            [compojure.api.sweet :refer :all]
            [rems.api.actions :refer [actions-api]]
            [rems.api.application :refer [application-api]]
            [rems.api.applications :refer [applications-api]]
            [rems.api.catalogue :refer [catalogue-api]]
            [rems.api.entitlements :refer [entitlements-api]]
            [rems.api.form :refer [form-api]]
            [rems.api.license :refer [license-api]]
            [rems.api.public :as public]
            [rems.api.resource :refer [resource-api]]
            [rems.api.workflow :refer [workflow-api]]
            [rems.context :as context]
            [rems.form :as form]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]
           rems.auth.NotAuthorizedException))

(defn unauthorized-handler
  [exception ex-data request]
  (unauthorized "unauthorized"))

(defn invalid-handler
  [exception ex-data request]
  (bad-request (.getMessage exception)))

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

     public/translations-api
     public/theme-api
     public/config-api

     actions-api
     application-api
     applications-api
     catalogue-api
     entitlements-api
     form-api
     license-api
     resource-api
     workflow-api)))
