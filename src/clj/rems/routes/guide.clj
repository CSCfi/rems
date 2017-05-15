(ns rems.routes.guide
  (:require [compojure.core :refer [GET defroutes]]
            [hiccup.core :as h]
            [hiccup.page :refer [include-js]]
            [rems.applicant-info :as applicant-info]
            [rems.applications :as applications]
            [rems.approvals :as approvals]
            [rems.cart :as cart]
            [rems.catalogue :as catalogue]
            [rems.collapsible :as collapsible]
            [rems.contents :as contents]
            [rems.context :as context]
            [rems.form :as form]
            [rems.guide :refer :all]
            [rems.phase :as phase]
            [rems.layout :as layout]
            [rems.role-switcher :as role-switcher]))

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

          [:h2 "Collapsible component"]
          (collapsible/guide)

          [:h2 "Applicant Information"]
          (applicant-info/guide)

          [:h2 "Forms"]
          (form/guide)

          [:h2 "Role switcher"]
          (role-switcher/guide)

          [:h2 "Misc components"]
          (phase/guide)
          (example "login" (contents/login "/"))
          (example "about" (contents/about))
          (include-js "/assets/jquery/jquery.min.js")
          (include-js "/assets/tether/dist/js/tether.min.js")
          (include-js "/assets/bootstrap/js/bootstrap.min.js")]]]))))

(defroutes guide-routes
  (GET "/guide" [] (guide-page)))
