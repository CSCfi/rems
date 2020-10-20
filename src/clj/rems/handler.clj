(ns rems.handler
  (:require [compojure.core :refer [GET defroutes routes wrap-routes]]
            [compojure.route :as route]
            [mount.core :as mount]
            [rems.api :refer [api-routes]]
            [rems.auth.auth :as auth]
            [rems.config :refer [env]]
            [rems.email.core] ;; to enable email polling
            [rems.entitlements :as entitlements]
            [rems.home :as home]
            [rems.layout :as layout]
            [rems.middleware :as middleware]
            [rems.util :refer [never-match-route]]
            [ring.util.response :as response]))

(defn not-found-handler [_req]
  ;; TODO: serve 404 for routes which the frontend doesn't recognize
  #_(layout/error-page {:status 404
                        :title "Page not found"})
  (layout/home-page))

(defn public-routes []
  (routes
   (home/home-routes)
   (auth/auth-routes)))

(defroutes secured-routes
  entitlements/entitlements-routes)

(defn- axe-routes
  "Serve axe.min.js through a symlink."
  []
  (fn [request]
    (when (= "/js/axe.min.js" (:uri request))
      (response/resource-response (:uri request) {:root "public" :allow-symlinks? true}))))

(defn normal-routes []
  (routes
   (public-routes)
   (wrap-routes #'secured-routes middleware/wrap-restricted)
   #'api-routes))

(defn extra-script-routes [{:keys [root files]}]
  (let [files (set files)]
    (fn [request]
      (when (contains? files (:uri request))
        (response/file-response (:uri request) {:root root})))))

(defn app-routes []
  (routes
   (extra-script-routes (:extra-scripts env))
   (when (:accessibility-tooling env) (axe-routes))
   (normal-routes)
   (if-let [path (:extra-static-resources env)]
     (route/files "/" {:root path})
     never-match-route)
   (if-let [path (:theme-static-resources env)]
     (route/files "/" {:root path})
     never-match-route)
   not-found-handler))

;; we use mount to construct the app so that middleware can access mount state
(mount/defstate handler
  :start (middleware/wrap-base (app-routes)))
