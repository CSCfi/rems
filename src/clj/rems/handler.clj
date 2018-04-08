(ns rems.handler
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes routes wrap-routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [rems.auth.auth :as auth]
            [rems.entitlements :as entitlements]
            [rems.env :refer [+defaults+]]
            [rems.events :as events]
            [rems.home :as home]
            [rems.landing-page :as landing-page]
            [rems.layout :refer [error-page]]
            [rems.middleware :as middleware]
            ;;[rems.routes.guide :refer [guide-routes]]
            [rems.api :refer [api-routes]]
            [rems.util :refer [never-match-route]]))

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

(defn public-routes []
  (routes
   home/home-routes
   (auth/auth-routes)))

(defroutes secured-routes
  landing-page/landing-page-routes
  events/events-routes
  entitlements/entitlements-routes)

(defn normal-routes []
  (routes
   (public-routes)
   (wrap-routes #'secured-routes middleware/wrap-restricted)
   #'api-routes))

(defn app-routes []
  (routes
   (normal-routes)
   #_(if (:component-guide +defaults+)
     guide-routes
     never-match-route)
   (if-let [path (:serve-static +defaults+)]
     (route/files "/" {:root path})
     never-match-route)
   not-found))

;; we use mount to construct the app so that middleware can access
;; mount state (e.g. rems.env/config)
(mount/defstate app
  :start (middleware/wrap-base (app-routes)))
