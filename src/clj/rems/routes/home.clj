(ns rems.routes.home
  (:require [rems.layout :as layout]
            [rems.contents :as contents]
            [rems.context :refer [*app-context*]]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [response redirect]]
            [buddy.auth.backends.session :refer [session-backend]]
            [clojure.java.io :as io]))

(defn home-page []
  (layout/render
    "home" (contents/login *app-context*)))

(defn about-page []
  (layout/render
    "about" (contents/about)))

(defn catalogue-page []
  (layout/render
    "catalogue" (contents/catalogue)))

(defroutes public-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))

(defroutes secured-routes
  (GET "/catalogue" [] (catalogue-page)))
