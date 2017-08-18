(ns rems.routes.home
  (:require [compojure.core :refer [GET defroutes]]
            [rems.applications :as applications]
            [rems.actions :as actions]
            [rems.css.styles :as styles]
            [rems.cart :as cart]
            [rems.catalogue :as catalogue]
            [rems.contents :as contents]
            [rems.context :as context]
            [rems.form :as form]
            [rems.landing-page :as landing-page]
            [rems.language-switcher :as language-switcher]
            [rems.layout :as layout]
            [rems.role-switcher :as role-switcher]
            [ring.util.response :refer [content-type
                                        redirect
                                        response]]))

(defn home-page []
  (if context/*user*
    (redirect "/landing_page")
    (layout/render
      "home" (contents/login context/*root-path*))))

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
  (GET "/css/screen.css" [] (-> (styles/generate-css)
                                (response)
                                (content-type "text/css")))
  language-switcher/switcher-routes)

(defroutes secured-routes
  (GET "/applications" [] (applications-page))
  (GET "/catalogue" [] (catalogue-page))
  landing-page/landing-page-routes
  actions/actions-routes
  cart/cart-routes
  form/form-routes
  role-switcher/role-switcher-routes)
