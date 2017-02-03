(ns rems.dev-handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [rems.layout :refer [error-page]]
            [rems.handler :as handler]
            [rems.routes.fake-shibboleth :refer [fake-shibboleth-routes]]
            [compojure.route :as route]
            [rems.env :refer [defaults]]
            [mount.core :as mount]
            [rems.middleware :as middleware]
            [clojure.tools.logging :as log]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def dev-routes
  (-> #'fake-shibboleth-routes
      (wrap-routes middleware/wrap-csrf)
      (wrap-routes middleware/wrap-formats)))

(def app-routes
  (routes
    #'handler/normal-routes
    dev-routes
    #'handler/not-found))

(def app (middleware/wrap-base #'app-routes))
