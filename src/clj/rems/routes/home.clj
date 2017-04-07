(ns rems.routes.home
  (:require [rems.layout :as layout]
            [rems.context :as context]
            [rems.catalogue :as catalogue]
            [rems.contents :as contents]
            [rems.applications :as applications]
            [rems.landing-page :as landing-page]
            [rems.approvals :as approvals]
            [rems.cart :as cart]
            [rems.form :as form]
            [rems.language-switcher :as language-switcher]
            [rems.role-switcher :as role-switcher]
            [compojure.core :refer [defroutes GET]]))

(defn home-page []
  (layout/render
    "home" (contents/login context/*root-path*)))

(defn about-page []
  (layout/render
    "about" (contents/about)))

(defn catalogue-page []
  (layout/render
    "catalogue" (catalogue/catalogue)))

(defn applications-page []
  (layout/render
   "applications"
   (applications/applications)))

(defroutes public-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  language-switcher/switcher-routes)

(defroutes secured-routes
  (GET "/applications" [] (applications-page))
  (GET "/catalogue" [] (catalogue-page))
  landing-page/landing-page-routes
  approvals/approvals-routes
  cart/cart-routes
  form/form-routes
  role-switcher/role-switcher-routes)
