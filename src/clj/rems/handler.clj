(ns rems.handler
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [routes wrap-routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [rems.auth.fake-shibboleth :refer [fake-shibboleth-routes]]
            [rems.auth.ldap :as ldap]
            [rems.config :refer [env]]
            [rems.env :refer [+defaults+]]
            [rems.layout :refer [error-page]]
            [rems.middleware :as middleware]
            [rems.routes.guide :refer [guide-routes]]
            [rems.routes.home :refer [public-routes secured-routes]]
            [rems.routes.services :refer [service-routes]]))

(mount/defstate init-app
  :start ((or (:init +defaults+) identity))
  :stop  ((or (:stop +defaults+) identity)))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []
  (doseq [component (:started (mount/start))]
    (log/info component "started")))

(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents)
  (log/info "Rems has shut down!"))

(defn not-found [req]
  (error-page {:status 404
               :title "Page not found"}))

(defn normal-routes []
  (routes
   #'public-routes
   (wrap-routes #'secured-routes middleware/wrap-restricted)
   #'service-routes))

(def never-match-route
  (constantly nil))

(defn app-routes []
  (routes
   (normal-routes)
   (if (:component-guide +defaults+)
     guide-routes
     never-match-route)
   (if (= (:authentication env) :fake-shibboleth)
     fake-shibboleth-routes
     never-match-route)
   ;; for the time being, only expose ldap auth in "dev mode"
   ;; together with fake-shibboleth
   (if (= (:authentication env) :fake-shibboleth)
     ldap/ldap-routes
     never-match-route)
   (if-let [path (:serve-static +defaults+)]
     (route/files "/" {:root path})
     never-match-route)
   not-found))

;; we use mount to construct the app so that middleware can access
;; mount state (e.g. rems.env/config)
(mount/defstate app
  :start (middleware/wrap-base (app-routes)))
