(ns rems.routes.home
  (:require [compojure.core :refer [GET defroutes]]
            [rems.actions :as actions]
            [rems.applications :as applications]
            [rems.cart :as cart]
            [rems.catalogue :as catalogue]
            [rems.contents :as contents]
            [rems.context :as context]
            [rems.css.styles :as styles]
            [rems.entitlements :as entitlements]
            [rems.events :as events]
            [rems.form :as form]
            [rems.landing-page :as landing-page]
            [rems.language-switcher :as language-switcher]
            [rems.layout :as layout]
            [rems.ldap :as ldap]
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
  language-switcher/switcher-routes
  ldap/ldap-routes)

(defroutes secured-routes
  (GET "/applications" [] (applications-page))
  (GET "/catalogue" [] (catalogue-page))
  (GET "/actions" [] (actions/actions-page))
  landing-page/landing-page-routes
  events/events-routes
  cart/cart-routes
  form/form-routes
  entitlements/entitlements-routes)
