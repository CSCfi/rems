(ns rems.auth.auth
  (:require [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [buddy.auth.protocols]
            [compojure.core :refer [GET routes]]
            [rems.auth.fake-login :as fake-login]
            [rems.auth.oidc :as oidc]
            [rems.auth.shibboleth :as shibboleth]
            [rems.config :refer [env]]
            [rems.db.api-key :as api-key]
            [rems.db.users :as users]
            [rems.util :refer [never-match-route]]
            [ring.util.response :refer [redirect]]))

(defn get-api-key [request]
  (get-in request [:headers "x-rems-api-key"]))

(defn get-api-user [request]
  (get-in request [:headers "x-rems-user-id"]))

(defn- api-key-backend []
  (reify
    buddy.auth.protocols/IAuthentication
    (-parse [_ request]
      {})
    (-authenticate [_ request _]
      (when (:uses-valid-api-key? request)
        (when-let [uid (get-api-user request)]
          (merge {:eppn uid}
                 ;; we need the raw user attrs here to emulate other login methods
                 (users/get-raw-user-attributes uid)))))))

(defn- auth-backends []
  (let [backend (case (:authentication env)
                  :shibboleth (shibboleth/backend)
                  (session-backend))]
    [(api-key-backend) backend]))

(defn- wrap-uses-valid-api-key [handler]
  (fn [request]
    (handler (assoc request :uses-valid-api-key? (api-key/valid? (get-api-key request)
                                                                 (get-api-user request)
                                                                 (:request-method request)
                                                                 (:uri request))))))

(defn wrap-auth [handler]
  (wrap-uses-valid-api-key
   (apply wrap-authentication handler (auth-backends))))

(defn- login-url []
  (case (:authentication env)
    :shibboleth (shibboleth/login-url)
    :oidc (oidc/login-url)
    :fake (fake-login/login-url)))

(defn- logout-url []
  (case (:authentication env)
    :shibboleth (shibboleth/logout-url)
    :oidc (oidc/logout-url)
    :fake (fake-login/logout-url)))

(defn auth-routes []
  (routes
   (GET "/logout" _ (redirect (logout-url)))
   (GET "/login" _ (redirect (login-url)))
   (case (:authentication env)
     :shibboleth never-match-route ; shibboleth routes handled by tomcat
     :oidc oidc/routes
     :fake fake-login/routes)))
