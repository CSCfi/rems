(ns rems.auth.auth
  (:require [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication
                                           wrap-authorization]]
            [compojure.core :refer [GET routes]]
            [rems.auth.shibboleth :as shibboleth]
            [rems.auth.fake-shibboleth :as fake-shibboleth]
            [rems.auth.ldap :as ldap]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.guide :refer [example]]
            [rems.util :refer [never-match-route]]
            [ring.util.response :refer [redirect]]))

(defn- wrap-auth-default
  "The standard auth middleware to use for non-shibboleth methods."
  [handler]
  (let [backend (session-backend)]
    (-> handler
        (wrap-authentication backend)
        (wrap-authorization backend))))

(defn wrap-auth [handler]
  (case (:authentication env)
    :shibboleth (shibboleth/wrap-auth handler)
    (wrap-auth-default handler)))

(defn login-component []
  (case (:authentication env)
    :shibboleth (shibboleth/login-component)
    :fake-shibboleth (shibboleth/login-component)
    :ldap (ldap/login-component)))

(defn- logout-url []
  (case (:authentication env)
    :shibboleth (shibboleth/logout-url)
    :fake-shibboleth (fake-shibboleth/logout-url)
    :ldap (ldap/logout-url)))

(defn auth-routes []
  (routes
   (GET "/logout" _ (redirect (logout-url)))
   (case (:authentication env)
     :shibboleth never-match-route ; shibboleth routes handled by tomcat
     ;; for the time being, expose ldap auth in "dev mode" together
     ;; with fake-shibboleth
     :fake-shibboleth (routes
                       fake-shibboleth/routes
                       ldap/routes)
     :ldap ldap/routes)))

(defn guide []
  (list
   (example "shibboleth login" (shibboleth/login-component))
   (example "ldap login" (ldap/login-component))))
