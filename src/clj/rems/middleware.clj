(ns rems.middleware
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [clojure.tools.logging :as log]
            [rems.auth.auth :as auth]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.api-key :as api-key]
            [rems.db.roles :as roles]
            [rems.env :refer [+defaults+]]
            [rems.layout :refer [error-page]]
            [rems.locales :refer [tempura-config]]
            [rems.util :refer [get-user-id]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults
                                              wrap-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.http-response :refer [unauthorized]]
            [ring.util.response :refer [redirect]]
            [taoensso.tempura :as tempura])
  (:import (javax.servlet ServletContext)))

(defn calculate-root-path [request]
  (if-let [context (:servlet-context request)]
    ;; If we're not inside a servlet environment
    ;; (for example when using mock requests), then
    ;; .getContextPath might not exist
    (try (.getContextPath ^ServletContext context)
         (catch IllegalArgumentException _ context))
    ;; if the context is not specified in the request
    ;; we check if one has been specified in the environment
    ;; instead
    (:app-context env)))

(defn get-api-key [request]
  (get-in request [:headers "x-rems-api-key"]))

(defn valid-api-key? [request]
  (when-let [key (get-api-key request)]
    (api-key/valid? key)))

(defn wrap-api-key-or-csrf-token
  "Custom wrapper for CSRF so that the API requests with valid `x-rems-api-key` don't need to provide CSRF token."
  [handler]
  (let [csrf-handler (wrap-anti-forgery handler)]
    (fn [request]
      (cond
        (valid-api-key? request) (handler request)
        (get-api-key request) (unauthorized "invalid api key")
        true (csrf-handler request)))))

(defn- wrap-user
  "Binds context/*user* to the buddy identity _or_ to x-rems-user-id if a valid api key is supplied."
  [handler]
  (fn [request]
    (let [header-identity (when-let [uid (get-in request [:headers "x-rems-user-id"])]
                            {"eppn" uid})
          session-identity (:identity request)]
      (binding [context/*user* (if (and header-identity
                                        (valid-api-key? request))
                                 header-identity
                                 session-identity)]
        (handler request)))))

(defn wrap-context [handler]
  (fn [request]
    (binding [context/*root-path* (calculate-root-path request)
              context/*flash* (:flash request)
              context/*roles* (when context/*user*
                                (roles/get-roles (get-user-id)))]
      (handler request))))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t)
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn wrap-formats [handler]
  (let [wrapped (wrap-restful-format
                 handler
                 {:formats [:json-kw :transit-json :transit-msgpack]})]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(defn on-restricted-page [request response]
  (assoc (redirect "/login")
         :session (assoc (:session response) :redirect-to (:uri request))))

(defn wrap-restricted
  [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-restricted-page}))

(defn- wrap-tempura-locales-from-session
  [handler]
  (fn [request]
    (handler
     (if-let [lang (get-in request [:session :language])]
       (assoc request :tr-locales [lang])
       request))))

(defn wrap-i18n
  "Wraps tempura into both the request as well as dynamic context."
  [handler]
  (wrap-tempura-locales-from-session
   (tempura/wrap-ring-request
    (fn [request]
      (binding [context/*tempura* (:tempura/tr request)
                context/*lang* (or (get-in request [:param :lang])
                                   (get-in request [:session :language])
                                   (:default-language env))]
        (handler request)))
    {:tr-opts (tempura-config)})))

(defn on-unauthorized-error [request]
  (error-page
   {:status 403
    :title (str "Access to " (:uri request) " is not authorized")}))

(defn wrap-unauthorized
  "Handles unauthorized exceptions by showing an error page."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch rems.auth.NotAuthorizedException e
        (on-unauthorized-error req)))))

(defn wrap-logging
  [handler]
  (fn [request]
    (let [uri (str (:uri request)
                   (when-let [q (:query-string request)]
                     (str "?" q)))]
      (log/info ">" (:request-method request) uri
                "lang:" context/*lang*
                "user:" context/*user*
                "roles:" context/*roles*)
      (log/debug "session" (pr-str (:session request)))
      (when-not (empty? (:form-params request))
        (log/debug "form params" (pr-str (:form-params request))))
      (let [response (handler request)]
        (log/info "<" (:request-method request) uri (:status response)
                  (or (get-in response [:headers "Location"]) ""))
        response))))

(def +wrap-defaults-settings+
  (-> site-defaults
      (assoc-in [:security :anti-forgery] false)
      (assoc-in [:session :store] (ttl-memory-store (* 60 30)))
      (assoc-in [:session :flash] true)))

(defn wrap-base [handler]
  (-> ((:middleware +defaults+) handler)
      wrap-unauthorized
      wrap-logging
      wrap-i18n
      wrap-context
      wrap-user
      wrap-api-key-or-csrf-token
      auth/wrap-auth
      wrap-webjars
      (wrap-defaults +wrap-defaults-settings+)
      wrap-internal-error
      wrap-i18n ; rendering the error page fails if rems.context/*tempura* is not set
      wrap-formats))
