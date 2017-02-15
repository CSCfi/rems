(ns rems.routes.home
  (:require [rems.layout :as layout]
            [rems.contents :as contents]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [response redirect]]
            [buddy.auth.backends.session :refer [session-backend]]
            [clojure.java.io :as io]))

(defn home-page []
  (layout/render
    (contents/login layout/*app-context*)))

(defn about-page []
  (layout/render
    (contents/about)))

(defn catalogue-page []
  (layout/render
    (contents/catalogue)))

(defroutes public-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))

(defroutes secured-routes
  (GET "/catalogue" [] (catalogue-page)))
