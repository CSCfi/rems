(ns rems.auth.auth
  (:require [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [buddy.auth.protocols]
            [compojure.core :refer [GET routes]]
            [rems.auth.fake-shibboleth :as fake-shibboleth]
            [rems.auth.ldap :as ldap]
            [rems.auth.oidc :as oidc]
            [rems.auth.shibboleth :as shibboleth]
            [rems.config :refer [env]]
            [rems.db.api-key :as api-key]
            [rems.util :refer [never-match-route]]
            [ring.util.response :refer [redirect]]))

(defn- api-key-backend []
  (reify
    buddy.auth.protocols/IAuthentication
    (-parse [_ request]
      {:key (get-in request [:headers "x-rems-api-key"])
       :user (when-let [uid (get-in request [:headers "x-rems-user-id"])]
               {:eppn uid})})
    (-authenticate [_ request {:keys [key user]}]
      (when (api-key/valid? key)
        user))))

(defn- auth-backends []
  (let [backend (case (:authentication env)
                  :shibboleth (shibboleth/backend)
                  (session-backend))]
    [(api-key-backend) backend]))

(defn wrap-auth [handler]
  (apply wrap-authentication handler (auth-backends)))

(defn- login-url []
  (case (:authentication env)
    :shibboleth (shibboleth/login-url)
    :fake-shibboleth (fake-shibboleth/login-url)
    :oidc (oidc/login-url)
    :ldap (ldap/login-url)))

(defn- logout-url []
  (case (:authentication env)
    :shibboleth (shibboleth/logout-url)
    :fake-shibboleth (fake-shibboleth/logout-url)
    :oidc (oidc/logout-url)
    :ldap (ldap/logout-url)))

(defn auth-routes []
  (routes
   (GET "/logout" _ (redirect (logout-url)))
   (GET "/login" _ (redirect (login-url)))
   (case (:authentication env)
     :shibboleth never-match-route ; shibboleth routes handled by tomcat
     ;; for the time being, expose ldap auth in "dev mode" together
     ;; with fake-shibboleth
     :fake-shibboleth (routes
                       fake-shibboleth/routes
                       ldap/routes)
     :ldap ldap/routes
     :oidc oidc/routes)))
