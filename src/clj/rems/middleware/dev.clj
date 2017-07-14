(ns rems.middleware.dev
  (:require [prone.middleware :refer [wrap-exceptions]]
            [rems.config :refer [env]]
            [rems.context :as context]
            [ring.middleware.reload :refer [wrap-reload]]))

(defn wrap-styles-context
  "Wraps context with the current theme configuration for rapid theme prototyping purposes."
  [handler]
  (fn [request]
    (binding [context/*theme* (context/load-theme)]
      (handler request))))

(defn wrap-some-exceptions
  "Wrap some exceptions in the prone.middleware/wrap-exceptions,
  but let others pass (i.e. `NotAuthorizedException`)."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch rems.auth.NotAuthorizedException e
        (throw e))
      (catch Throwable e
        ((wrap-exceptions (fn [& _] (throw e))) req)))))

(defn wrap-dev
  "Middleware for dev use. Autoreload, style reloading, nicer errors."
  [handler]
  (-> handler
      wrap-reload
      wrap-styles-context
      wrap-some-exceptions))
