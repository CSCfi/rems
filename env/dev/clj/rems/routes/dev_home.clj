(ns rems.routes.dev-home
  (:require [rems.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [response redirect]]
            [buddy.auth.backends.session :refer [session-backend]]
            [clojure.java.io :as io]))

(defn- dev-login [{session :session}]
      (assoc (redirect "/catalogue")
             :session (assoc session :identity "developer")))

(defn- dev-logout [{session :session}]
  (-> (redirect "/")
      (assoc :session (dissoc session :identity))))

(defroutes login-routes
  (GET "/Shibboleth.sso/Login" req (dev-login req))
  (GET "/logout" req (dev-logout req)))
