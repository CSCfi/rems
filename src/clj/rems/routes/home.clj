(ns rems.routes.home
  (:require [rems.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [response redirect]]
            [buddy.auth.backends.session :refer [session-backend]]
            [clojure.java.io :as io]))

(defn home-page []
  (layout/render
    "home.html"))

(defn about-page []
  (layout/render "about.html"))

(defn catalogue-page []
  (layout/render "catalogue.html"))

(defroutes public-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))

(defroutes secured-routes
  (GET "/catalogue" [] (catalogue-page)))

(defn- dev-login [{session :session}]
      (assoc (redirect "/catalogue")
             :session (assoc session :identity "developer")))

(defn- dev-logout [{session :session}]
  (-> (redirect "/")
      (assoc :session (dissoc session :identity))))

(defroutes dev-routes
  (GET "/Shibboleth.sso/Login" req (dev-login req))
  (GET "/logout" req (dev-logout req)))

