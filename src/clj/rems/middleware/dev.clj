(ns rems.middleware.dev
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [selmer.middleware :refer [wrap-error-page]]
            [prone.middleware :refer [wrap-exceptions]]))

(defn wrap-dev
  "Middleware for dev use. Autoreload, nicer errors."
  [handler]
  (-> handler
      wrap-reload
      wrap-error-page
      wrap-exceptions))
