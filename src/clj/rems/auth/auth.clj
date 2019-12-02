(ns rems.auth.auth
  (:require [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [compojure.core :refer [GET routes]]
            [rems.auth.fake-shibboleth :as fake-shibboleth]
            [rems.auth.ldap :as ldap]
            [rems.auth.oidc :as oidc]
            [rems.auth.shibboleth :as shibboleth]
            [rems.config :refer [env]]
            [rems.util :refer [never-match-route]]
            [ring.util.response :refer [redirect]]))

(defn- wrap-auth-default
  "The standard auth middleware to use for non-shibboleth methods."
  [handler]
  (let [backend (session-backend)]
    (-> handler
        (wrap-authentication backend))))

(defn wrap-auth [handler]
  (case (:authentication env)
    :shibboleth (shibboleth/wrap-auth handler)
    (wrap-auth-default handler)))

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
