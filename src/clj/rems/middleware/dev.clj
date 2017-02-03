(ns rems.middleware.dev
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [selmer.middleware :refer [wrap-error-page]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [prone.middleware :refer [wrap-exceptions]]))

(defn wrap-session-auth
  "For use with rems.routes.fake-shibboleth"
  [handler]
  (let [backend (session-backend)]
    (-> handler
        (wrap-authentication backend)
        (wrap-authorization backend))))

(defn wrap-dev
  "Middleware for dev use. Fake auth, autoreload, nicer errors."
  [handler]
  (-> handler
      wrap-session-auth
      wrap-reload
      wrap-error-page
      wrap-exceptions))
