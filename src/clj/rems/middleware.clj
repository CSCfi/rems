(ns rems.middleware
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [clojure.tools.logging :as log]
            [rems.auth.auth :as auth]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.roles :as roles]
            [rems.env :refer [+defaults+]]
            [rems.layout :refer [error-page]]
            [rems.locales :refer [tconfig]]
            [rems.util :refer [get-user-id]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults
                                              wrap-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.response :refer [redirect]]
            [taoensso.tempura :as tempura])
  (:import (javax.servlet ServletContext)))

(def +default-language+ :en)

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

(defn wrap-webapp-context
  "Wraps context with data specific to the webapp usage. I.e. not things needed by REST service or SPA."
  [handler]
  (fn [request]
    (binding [context/*root-path* (calculate-root-path request)
              context/*flash* (:flash request)]
      (handler request))))

;; TODO handle using API-key and representing someone
(defn wrap-service-context
  "Wraps context with data specific to the service usage. I.e. things needed by REST service or SPA."
  [handler]
  (fn [request]
    (if (and (:uri request) (.startsWith (:uri request) "/api"))
      (binding [context/*lang* (get-in request [:params :lang])
                context/*user* {"eppn" (get-in request [:headers "x-rems-user-id"])}]
        (handler request))
      (handler request))))

(defn wrap-context [handler]
  (fn [request]
    (binding [context/*roles* (when context/*user*
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
                context/*lang* (get-in request [:session :language] +default-language+)]
        (handler request)))
    {:tr-opts tconfig})))

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

(defn- wrap-user
  "Binds context/*user* to the buddy identity."
  [handler]
  (fn [request]
    (binding [context/*user* (:identity request)]
      (handler request))))

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

;; TODO proper API key handling
(defn valid-api-key? [key]
  (= "42" key))

(defn wrap-csrf
  "Custom wrapper for CSRF so that the API requests with valid `x-rems-api-key` don't need to provide CSRF token."
  [handler]
  (let [csrf-handler (wrap-anti-forgery handler)]
    (fn [request]
      (if (valid-api-key? (get-in request [:headers "x-rems-api-key"]))
        (handler request)
        (csrf-handler request)))))

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
      wrap-webapp-context
      wrap-service-context
      wrap-user
      auth/wrap-auth
      wrap-webjars
      wrap-csrf
      (wrap-defaults +wrap-defaults-settings+)
      wrap-internal-error
      wrap-formats))
