(ns rems.api
  (:require [cheshire.generate :as cheshire]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [compojure.api.exception :as ex]
            [compojure.api.sweet :refer :all]
            [muuntaja.core :as muuntaja]
            [muuntaja.format.json :refer [json-format]]
            [muuntaja.format.transit :as transit-format]
            [rems.api.actions :refer [actions-api]]
            [rems.api.applications :refer [applications-api]]
            [rems.api.catalogue :refer [catalogue-api]]
            [rems.api.catalogue-items :refer [catalogue-items-api]]
            [rems.api.entitlements :refer [entitlements-api]]
            [rems.api.forms :refer [forms-api]]
            [rems.api.licenses :refer [licenses-api]]
            [rems.api.public :as public]
            [rems.api.resources :refer [resources-api]]
            [rems.api.users :refer [users-api]]
            [rems.api.workflows :refer [workflows-api]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (org.joda.time DateTime ReadableInstant)
           (rems InvalidRequestException)
           (rems.auth NotAuthorizedException)))

(defn unauthorized-handler
  [exception ex-data request]
  (log/info "User is unauthorized")
  (unauthorized "unauthorized"))

(defn invalid-handler
  [exception ex-data request]
  (bad-request (.getMessage exception)))

(def cors-middleware
  #(wrap-cors
    %
    :access-control-allow-origin #".*"
    :access-control-allow-methods [:get :put :post :delete]))

(def joda-time-writer
  (transit/write-handler
   "m"
   (fn [v] (-> ^ReadableInstant v .getMillis))
   (fn [v] (-> ^ReadableInstant v .getMillis .toString))))

(cheshire/add-encoder
 DateTime
 (fn [c jsonGenerator]
   (.writeString jsonGenerator (-> ^ReadableInstant c .getMillis .toString))))

(def muuntaja
  (muuntaja/create
   (update
    muuntaja/default-options
    :formats
    merge
    {"application/json"
     json-format

     "application/transit+json"
     {:decoder [(partial transit-format/make-transit-decoder :json)]
      :encoder [#(transit-format/make-transit-encoder
                  :json
                  (merge
                   %
                   {:handlers {DateTime joda-time-writer}}))]}})))

(defn slow-middleware [request]
  (Thread/sleep 2000)
  request)

(def api-routes
  (api
   {;; TODO: should this be in rems.middleware?
    :formats muuntaja
    :middleware [cors-middleware]
    :exceptions {:handlers {NotAuthorizedException unauthorized-handler
                            InvalidRequestException (ex/with-logging invalid-handler)
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
     ;; :middleware [slow-middleware]
     :header-params [{x-rems-api-key :- (describe s/Str "REMS API-Key (optional for UI, required for API)") nil}
                     {x-rems-user-id :- (describe s/Str "user id (optional for UI, required for API)") nil}]

     public/translations-api
     public/theme-api
     public/config-api

     actions-api
     applications-api
     catalogue-api
     catalogue-items-api
     entitlements-api
     forms-api
     licenses-api
     resources-api
     users-api
     workflows-api)))
