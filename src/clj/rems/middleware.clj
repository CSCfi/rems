(ns rems.middleware
  (:require [rems.env :refer [+defaults+]]
            [clojure.tools.logging :as log]
            [rems.layout :refer [error-page]]
            [rems.context :as context]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [rems.config :refer [env]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [taoensso.tempura :as tempura :refer [tr]]
            [rems.locales :refer [tconfig]]
            [rems.auth.backend :refer [shibbo-backend authz-backend]])
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
    (binding [context/*root-path* (calculate-root-path request)]
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

(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))

(defn wrap-formats [handler]
  (let [wrapped (wrap-restful-format
                  handler
                  {:formats [:json-kw :transit-json :transit-msgpack]})]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(defn on-error [request response]
  (error-page
    {:status 403
     :title (str "Access to " (:uri request) " is not authorized")}))

(defn wrap-restricted [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-error}))

(defn wrap-i18n [handler]
  (tempura/wrap-ring-request handler {:tr-opts tconfig}))

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

(defn wrap-base [handler]
  (-> ((:middleware +defaults+) handler)
      wrap-i18n
      wrap-auth
      wrap-webjars
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (assoc-in  [:session :store] (ttl-memory-store (* 60 30)))))
      wrap-context
      wrap-internal-error
      wrap-csrf
      wrap-formats))
