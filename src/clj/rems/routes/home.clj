(ns rems.routes.home
  (:require [compojure.core :refer [GET defroutes routes]]
            [rems.actions :as actions]
            [rems.applications :as applications]
            [rems.auth.auth :as auth]
            [rems.cart :as cart]
            [rems.catalogue :as catalogue]
            [rems.context :as context]
            [rems.css.styles :as styles]
            [rems.entitlements :as entitlements]
            [rems.events :as events]
            [rems.form :as form]
            [rems.landing-page :as landing-page]
            [rems.language-switcher :as language-switcher]
            [rems.layout :as layout]
            [rems.text :refer [text]]
            [ring.util.response :refer [content-type
                                        redirect
                                        response]]))

(defn- about []
  [:p (text :t.about/text)])

(defn about-page []
  (layout/render
    "about" (about)))

(defn home-page []
  (if context/*user*
    (redirect "/landing_page")
    (layout/render "home" (auth/login-component))))

(defn catalogue-page []
  (layout/render
    "catalogue" (catalogue/catalogue)))

(defn applications-page []
  (layout/render
   "applications"
   (applications/applications)))

(defn public-routes []
  (routes
   (GET "/" [] (home-page))
   (GET "/about" [] (about-page))
   (GET "/css/screen.css" [] (-> (styles/generate-css)
                                 (response)
                                 (content-type "text/css")))
   language-switcher/switcher-routes
   (auth/auth-routes)))

(defroutes secured-routes
  (GET "/applications" [] (applications-page))
  (GET "/catalogue" [] (catalogue-page))
  (GET "/actions" [] (actions/actions-page))
  landing-page/landing-page-routes
  events/events-routes
  cart/cart-routes
  form/form-routes
  entitlements/entitlements-routes)
