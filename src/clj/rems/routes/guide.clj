(ns rems.routes.guide
  (:require [compojure.core :refer [GET defroutes]]
            [hiccup.core :as h]
            [rems.applications :as applications]
            [rems.approvals :as approvals]
            [rems.cart :as cart]
            [rems.catalogue :as catalogue]
            [rems.contents :as contents]
            [rems.context :as context]
            [rems.db.core :as db]
            [rems.form :as form]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.locales :as locales]
            [rems.role-switcher :as role-switcher]
            [taoensso.tempura :as tempura :refer [tr]]))

(defn color-box [id hex]
  [:div.col-md-3
   [:row
    [:div.col-md-6.rectangle {:class id }]
    [:div.col-md-6.color-title hex]]])

(defn color-boxes []
  [:div.row
   (color-box "color-1" "#CAD2E6")
   (color-box "color-2" "#7A90C3")
   (color-box "color-3" "#4D5A91")
   (color-box "color-4" "#F16522")])

(defn guide-page []
  (binding [context/*root-path* "path/"]
    (with-language :en
      (h/html
       [:head
        [:link {:type "text/css" :rel "stylesheet" :href "/assets/bootstrap/css/bootstrap.min.css"}]
        [:link {:type "text/css" :rel "stylesheet" :href "/assets/font-awesome/css/font-awesome.min.css"}]
        [:link {:type "text/css" :rel "stylesheet" :href "/css/screen.css"}]]
       [:body
        [:div.container
         [:div.example-page
          [:h1 "Component Guide"]

          [:h2 "Colors"]
          (example "" (color-boxes))

          [:h2 "Layout components"]
          (layout/guide)

          [:h2 "Catalogue components"]
          (catalogue/guide)

          [:h2 "Cart components"]
          (cart/guide)

          [:h2 "Applications list"]
          (applications/guide)

          [:h2 "Approvals list"]
          (approvals/guide)

          [:h2 "Forms"]
          (form/guide)

          [:h2 "Role switcher"]
          (role-switcher/guide)

          [:h2 "Misc components"]
          (example "login" (contents/login "/"))
          (example "about" (contents/about))]]]))))

(defroutes guide-routes
  (GET "/guide" [] (guide-page)))
