(ns rems.api
  (:require [clj-time.core :as time]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.tools.logging :as log]
            [compojure.api.exception :as ex]
            [compojure.api.sweet :refer :all]
            [conman.core :as conman]
            [rems.api.applications :refer [applications-api my-applications-api]]
            [rems.api.audit-log :refer [audit-log-api]]
            [rems.api.blacklist :refer [blacklist-api]]
            [rems.api.catalogue :refer [catalogue-api]]
            [rems.api.catalogue-items :refer [catalogue-items-api]]
            [rems.api.categories :refer [categories-api]]
            [rems.api.email :refer [email-api]]
            [rems.api.entitlements :refer [entitlements-api]]
            [rems.api.extra-pages :refer [extra-pages-api]]
            [rems.api.forms :refer [forms-api]]
            [rems.api.health :refer [health-api]]
            [rems.api.invitations :refer [invitations-api]]
            [rems.api.licenses :refer [licenses-api]]
            [rems.api.organizations :refer [organizations-api]]
            [rems.api.permissions :refer [permissions-api]]
            [rems.api.public :as public]
            [rems.api.resources :refer [resources-api]]
            [rems.api.user-settings :refer [user-settings-api]]
            [rems.api.users :refer [users-api]]
            [rems.api.workflows :refer [workflows-api]]
            [rems.auth.auth :as auth]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.util :refer [get-user-id]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.http-response :refer :all]
            [ring.util.response :as response]
            [schema.core :as s])
  (:import clojure.lang.ExceptionInfo
           [rems.auth ForbiddenException UnauthorizedException]
           rems.DataException
           rems.InvalidRequestException
           rems.NotImplementedException
           rems.PayloadTooLargeException
           rems.UnsupportedMediaTypeException
           rems.TryAgainException))

(defn- plain-text [response]
  (response/content-type response "text/plain"))

(defn unauthorized-handler
  [exception ex-data request]
  (log/info "unauthorized" (.getMessage exception))
  (-> (unauthorized "unauthorized")
      (plain-text)))

(defn forbidden-handler
  [exception ex-data request]
  (log/info "forbidden" (.getMessage exception))
  (-> (forbidden (or (.getMessage exception) "forbidden"))
      (plain-text)))

(defn invalid-handler
  [exception ex-data request]
  (log/info "bad-request" (.getMessage exception))
  (-> (bad-request (.getMessage exception))
      (plain-text)))

(defn debug-handler
  [exception ex-data request]
  (-> (internal-server-error (with-out-str (print-cause-trace exception)))
      (plain-text)))

(defn not-found-handler
  [request]
  (-> (not-found "not found")
      (plain-text)))

(defn try-again-handler
  [exception _ex-data _request]
  (log/error "try again" exception)
  (-> (service-unavailable "please try again")
      (plain-text)))

(defn data-exception-handler
  [exception _ex-data _request]
  (log/error "data exception" (pr-str (.-data exception)))
  (-> (service-unavailable (json/generate-string (.-data exception)))
      (response/content-type "application/json")))

(defn ex-info-handler
  [exception ex-data _request]
  (log/error exception (str (.getMessage exception) " " (pr-str ex-data)))
  (-> (internal-server-error)
      (response/content-type "application/json")))

(defn payload-too-large-handler
  [exception _ex-data _request]
  (log/error exception (.getMessage exception))
  (-> (request-entity-too-large)
      (response/content-type "application/json")))

(defn unsupported-media-type-handler
  [exception _ex-data _request]
  (log/error exception (.getMessage exception))
  (-> (unsupported-media-type)
      (response/content-type "application/json")))

(defn not-implemented-handler
  [exception _ex-data _request]
  (log/error exception (.getMessage exception))
  (-> (not-implemented)
      (response/content-type "application/json")))

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

;; This should be run outside transaction-middleware since we want to
;; write even on GET queries. We're only running one insert statement
;; so we don't need a separate transaction for logging.
(defn audit-log-middleware [handler]
  (fn [request]
    (let [response (handler request)]
      (try
        (db/add-to-audit-log! {:time (time/now)
                               :path (:uri request)
                               :method (name (:request-method request))
                               :apikey (auth/get-api-key request)
                               :userid (get-user-id)
                               :status (str (:status response))})
        (catch Throwable t
          (log/error "Adding to audit log failed:" t)))
      response)))

(defn transaction-middleware [handler]
  (fn [request]
    (conman/with-transaction [rems.db.core/*db* {:isolation :serializable
                                                 :read-only? (read-only? request)}]
      (handler request))))

(defn slow-middleware [request]
  (Thread/sleep 2000)
  request)

(def api-routes
  ;; we wrap audit-log-middleware outside compojure-api since we want
  ;; to see the status codes produced by our exception handlers
  (audit-log-middleware
   (api
    {;; TODO: should this be in rems.middleware?
     :formats json/muuntaja
     :middleware [cors-middleware
                  transaction-middleware]
     :exceptions {:handlers {UnauthorizedException unauthorized-handler
                             ExceptionInfo ex-info-handler
                             ForbiddenException forbidden-handler
                             InvalidRequestException invalid-handler
                             TryAgainException try-again-handler
                             DataException data-exception-handler
                             PayloadTooLargeException payload-too-large-handler
                             UnsupportedMediaTypeException unsupported-media-type-handler
                             NotImplementedException not-implemented-handler
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
                      {x-rems-user-id :- (describe s/Str "user (optional for UI, required for API). This can be a REMS internal or an external user identity attribute (specified in config.edn).") nil}]

      public/translations-api
      public/theme-api
      public/config-api
      public/keepalive-api

      my-applications-api
      applications-api
      audit-log-api
      blacklist-api
      catalogue-api
      catalogue-items-api
      categories-api
      email-api
      entitlements-api
      extra-pages-api
      forms-api
      health-api
      invitations-api
      licenses-api
      organizations-api
      permissions-api
      resources-api
      user-settings-api
      users-api
      workflows-api

      ;; keep this last
      (undocumented not-found-handler)))))
