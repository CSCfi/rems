(ns rems.middleware.dev
  (:require [rems.context :as context]
            [ring.middleware.reload :refer [wrap-reload]]))

(defn wrap-styles-context
  "Wraps context with the current theme configuration for rapid theme prototyping purposes."
  [handler]
  (fn [request]
    (binding [context/*theme* (context/load-theme)]
      (handler request))))

(defn wrap-dev
  "Middleware for dev use. Autoreload, style reloading."
  [handler]
  (-> handler
      wrap-reload
      wrap-styles-context))
