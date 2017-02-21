(ns rems.routes.home
  (:require [rems.db.core :as db]
            [rems.layout :as layout]
            [rems.context :as context]
            [rems.contents :as contents]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [response redirect]]
            [buddy.auth.backends.session :refer [session-backend]]
            [clojure.java.io :as io]))

(defn home-page []
  (layout/render
    "home" (contents/login context/*root-path*)))

(defn about-page []
  (layout/render
    "about" (contents/about)))

(defn catalogue-page []
  (layout/render
    "catalogue" (contents/catalogue (db/get-catalogue-items))))

(defroutes public-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))

(defroutes secured-routes
  (GET "/catalogue" [] (catalogue-page)))
