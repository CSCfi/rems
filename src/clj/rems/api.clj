(ns rems.api
  (:require [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.tools.logging :as log]
            [compojure.api.exception :as ex]
            [compojure.api.sweet :refer :all]
            [conman.core :as conman]
            [rems.api.applications :refer [applications-api my-applications-api]]
            [rems.api.catalogue :refer [catalogue-api]]
            [rems.api.catalogue-items :refer [catalogue-items-api]]
            [rems.api.entitlements :refer [entitlements-api]]
            [rems.api.forms :refer [forms-api]]
            [rems.api.licenses :refer [licenses-api]]
            [rems.api.public :as public]
            [rems.api.resources :refer [resources-api]]
            [rems.api.users :refer [users-api]]
            [rems.api.workflows :refer [workflows-api]]
            [rems.json :refer [muuntaja]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [rems.auth ForbiddenException NotAuthorizedException]
           rems.InvalidRequestException))

(defn unauthorized-handler
  [exception ex-data request]
  (log/info "unauthorized" (.getMessage exception))
  (unauthorized "unauthorized"))

(defn forbidden-handler
  [exception ex-data request]
  (log/info "forbidden" (.getMessage exception))
  (forbidden "forbidden"))

(defn invalid-handler
  [exception ex-data request]
  (log/info "bad-request" (.getMessage exception))
  (bad-request (.getMessage exception)))

(defn debug-handler
  [exception ex-data request]
  (internal-server-error (with-out-str (print-cause-trace exception))))

(defn with-logging
  ;; Like in compojure.api.exception, but logs some of the data (with pprint)
  "Wrap compojure-api exception-handler a function which will log the
  exception message and stack-trace with given log-level."
  ([handler] (with-logging handler :error))
  ([handler log-level]
   {:pre [(#{:trace :debug :info :warn :error :fatal} log-level)]}
   (fn [^Exception e data req]
     (log/log log-level e (str (.getMessage e)
                               "\n"
                               (with-out-str
                                 (clojure.pprint/pprint
                                  (select-keys data [:schema :errors :response])))))
     (handler e data req))))

(def cors-middleware
  #(wrap-cors
    %
    :access-control-allow-origin #".*"
    :access-control-allow-methods [:get :put :post :delete]))

(defn- read-only? [request]
  (not (contains? #{:put :post} (:request-method request))))

(defn transaction-middleware [handler]
  (fn [request]
    (conman/with-transaction [rems.db.core/*db* {:isolation :serializable
                                                 :read-only? (read-only? request)}]
      (handler request))))

(defn slow-middleware [request]
  (Thread/sleep 2000)
  request)

(def api-routes
  (api
    {;; TODO: should this be in rems.middleware?
     :formats muuntaja
     :middleware [cors-middleware
                  transaction-middleware]
     :exceptions {:handlers {NotAuthorizedException unauthorized-handler
                             ForbiddenException forbidden-handler
                             InvalidRequestException invalid-handler
                             ;; java.lang.Throwable (ex/with-logging debug-handler) ; optional Debug handler
                             ;; add logging to validation handlers
                             ::ex/request-validation (with-logging ex/request-validation-handler)
                             ::ex/request-parsing (with-logging ex/request-parsing-handler)
                             ::ex/response-validation (with-logging ex/response-validation-handler)}}
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

      my-applications-api
      applications-api
      catalogue-api
      catalogue-items-api
      entitlements-api
      forms-api
      licenses-api
      resources-api
      users-api
      workflows-api)))
