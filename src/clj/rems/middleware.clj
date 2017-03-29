(ns rems.middleware
  (:require [rems.env :refer [+defaults+]]
            [clojure.tools.logging :as log]
            [rems.layout :refer [error-page]]
            [rems.context :as context]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [rems.cart :refer [get-cart-from-session]]
            [rems.db.roles :as roles]
            [rems.config :refer [env]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [taoensso.tempura :as tempura :refer [tr]]
            [rems.locales :refer [tconfig]]
            [rems.auth.backend :refer [shibbo-backend authz-backend]]
            [rems.language-switcher :refer [+default-language+]]
            [rems.auth.NotAuthorizedException])
  (:import [javax.servlet ServletContext]))

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

(defn wrap-context [handler]
  (fn [request]
    (binding [context/*root-path* (calculate-root-path request)
              context/*cart* (get-cart-from-session request)
              context/*flash* (:flash request)
              context/*roles* (when context/*user*
                                (roles/get-roles context/*user*))
              context/*active-role* (when context/*user*
                                      (roles/get-active-role context/*user*))]
      (handler request))))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t)
        (error-page {:status 500
                     :bare true ;; navbar requires tempura and we might not have it
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

(defn on-unauthorized-error [request response]
  (error-page
    {:status 403
     :title (str "Access to " (:uri request) " is not authorized")}))

(defn wrap-restricted [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-unauthorized-error}))

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

(defn wrap-unauthorized
  "Handles unauthorized exceptions by showing an error page."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch rems.auth.NotAuthorizedException e
        (on-unauthorized-error req nil)))))

(defn wrap-auth
  [handler]
  (let [authentication (if (:fake-shibboleth +defaults+)
                         (session-backend)
                         (shibbo-backend))
        authorization (if (:fake-shibboleth +defaults+)
                        authentication
                        (authz-backend))]
    (-> (fn [request]
          (binding [context/*user* (:identity request)]
            (handler request)))
        (wrap-authentication authentication)
        (wrap-authorization authorization))))

(def +wrap-defaults-settings+
  (-> site-defaults
      (assoc-in [:security :anti-forgery] true)
      (assoc-in [:session :store] (ttl-memory-store (* 60 30)))
      (assoc-in [:session :flash] true)))

(defn wrap-base [handler]
  (-> ((:middleware +defaults+) handler)
      wrap-unauthorized
      wrap-i18n
      wrap-context
      wrap-auth
      wrap-webjars
      (wrap-defaults +wrap-defaults-settings+)
      wrap-internal-error
      wrap-formats))
