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

(defn break-page []
  (layout/render "maintenance.html"))

(defroutes public-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  (GET "/break" [] (break-page)))

(defroutes secured-routes
  (GET "/catalogue" [] (catalogue-page)))
