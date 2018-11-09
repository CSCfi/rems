(ns rems.middleware.dev
  (:require [ring.middleware.reload :refer [wrap-reload]]))

(defn wrap-dev
  "Middleware for dev use. Autoreload, style reloading."
  [handler]
  (-> handler
      (wrap-reload {:dirs ["src" "resources"]})))
