(ns rems.dev-handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [rems.layout :refer [error-page]]
            [rems.routes.home :refer [public-routes secured-routes]]
            [rems.routes.dev-home :refer [dev-routes]]
            [compojure.route :as route]
            [rems.env :refer [defaults]]
            [mount.core :as mount]
            [rems.middleware :as middleware]
            [clojure.tools.logging :as log]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    (-> #'public-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (-> #'secured-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-restricted)
        (wrap-routes middleware/wrap-formats))
    (-> #'dev-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (route/not-found
      (:body
        (error-page {:status 404
                     :title "page not found"})))))

(def app (middleware/wrap-base #'app-routes))
