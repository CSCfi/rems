(ns rems.routes.home
  (:require [rems.layout :as layout]
            [rems.context :as context]
            [rems.contents :as contents]
            [rems.cart :as cart]
            [rems.language-switcher :as language-switcher]
            [compojure.core :refer [defroutes GET]]))

(defn home-page []
  (layout/render
    "home" (contents/login context/*root-path*)))

(defn about-page []
  (layout/render
    "about" (contents/about)))

(defn catalogue-page []
  (layout/render
    "catalogue" (contents/catalogue)))

(defroutes public-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  language-switcher/switcher-routes)

(defroutes secured-routes
  (GET "/catalogue" [] (catalogue-page))
  cart/cart-routes)
