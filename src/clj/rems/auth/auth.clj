(ns rems.auth.auth
  (:require [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [buddy.auth.protocols]
            [compojure.core :refer [GET routes]]
            [rems.auth.fake-login :as fake-login]
            [rems.auth.oidc :as oidc]
            [rems.config :refer [env]]
            [rems.db.api-key :as api-key]
            [rems.db.users :as users]
            [rems.db.user-mappings :as user-mappings]
            [ring.util.response :refer [redirect]]))

(defn get-api-key [request]
  (get-in request [:headers "x-rems-api-key"]))

(defn get-api-userid [request]
  (get-in request [:headers "x-rems-user-id"]))

(defn get-api-user [request]
  (-> request
      get-api-userid
      user-mappings/find-userid))

(defn- api-key-backend []
  (reify
    buddy.auth.protocols/IAuthentication
    (-parse [_ request]
      {})
    (-authenticate [_ request _]
      (when (:uses-valid-api-key? request)
        (when-let [uid (get-api-user request)]
          (merge {:userid uid}
                 ;; we need the raw user attrs here to emulate other login methods
                 (users/get-raw-user-attributes uid)))))))

(defn- auth-backends []
  [(api-key-backend) (session-backend)])

(defn- wrap-uses-valid-api-key [handler]
  (fn [request]

    (handler (assoc request :uses-valid-api-key?
                    (if-some [api-key (get-api-key request)] ; don't try to check without

                      (api-key/valid? api-key
                                      (get-api-user request)
                                      (:request-method request)
                                      (:uri request))
                      false)))))

(defn wrap-auth [handler]
  (wrap-uses-valid-api-key
   (apply wrap-authentication handler (auth-backends))))

(defn- login-url []
  (case (:authentication env)
    :oidc (oidc/login-url)
    :fake (fake-login/login-url)))

(defn- logout-url []
  (case (:authentication env)
    :oidc (oidc/logout-url)
    :fake (fake-login/logout-url)))

(defn auth-routes []
  (routes
   (GET "/logout" _ (redirect (logout-url)))
   (GET "/login" _ (redirect (login-url)))
   (case (:authentication env)
     :oidc oidc/routes
     :fake fake-login/routes)))
