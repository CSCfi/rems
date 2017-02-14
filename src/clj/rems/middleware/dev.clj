(ns rems.middleware.dev
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [prone.middleware :refer [wrap-exceptions]]))

(defn wrap-dev
  "Middleware for dev use. Autoreload, nicer errors."
  [handler]
  (-> handler
      wrap-reload
      wrap-exceptions))
