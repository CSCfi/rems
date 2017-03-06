(ns rems.routes.guide
  (:require [rems.layout :as layout]
            [rems.context :as context]
            [rems.contents :as contents]
            [hiccup.core :as h]
            [compojure.core :refer [defroutes GET]]
            [rems.locales :as locales]
            [taoensso.tempura :as tempura :refer [tr]]
            [rems.db.core :as db]
            ))

(def g-user "Eero Esimerkki")
(def g-tr (partial tr locales/tconfig [:fi]))

(defn example [name description content]
  [:div.example
   [:h3 name]
   (when description
     description)
   [:div.example-content content
    [:div.example-content-end]]])

(defn guide-page []
  (h/html
   [:head
    [:link {:type "text/css" :rel "stylesheet" :href "/assets/bootstrap/css/bootstrap.min.css"}]
    [:link {:type "text/css" :rel "stylesheet" :href "/assets/font-awesome/css/font-awesome.min.css"}]
    [:link {:type "text/css" :rel "stylesheet" :href "/css/screen.css"}]]
   [:body
    [:div.example-page
     [:h1 "Component Guide"]
     [:h2 "Layout components"]
     [:div.navbar-nav
      (example "nav-link" nil
               (layout/nav-link "example/path" "link text"))]
     [:div.navbar-nav
      (example "nav-link active" nil
               (layout/nav-link "example/path" "link text" "page-name" "page-name"))]
     [:div.navbar-nav
      (example "nav-item" nil
               (layout/nav-link "example/path" "link text" "page-name" "li-name"))]
     (example "primary-nav" nil
                (layout/primary-nav "page-name" g-user g-tr))
     (example "secondary-nav" nil
              (layout/secondary-nav g-user g-tr))
     (example "navbar" nil
              (layout/navbar "example-page" g-user g-tr))
     (example "footer" nil
              (layout/footer))
     [:h2 "Catalogue components"]
     (example "catalogue-item" nil
              [:table.ctlg-table
               (contents/catalogue-item {:title "Item title"})])
     (example "catalogue-item linked to urn.fi" nil
              [:table.ctlg-table
               (contents/catalogue-item {:title "Item title" :resid "http://urn.fi/urn:nbn:fi:lb-201403262"})])
     (example "catalogue with two items" nil
              (contents/catalogue-list [{:title "Item title"} {:title "Another title"}]))
     [:h2 "Cart components"]
     (example "cart-item" nil
              [:table.ctlg-table
               (contents/cart-item "Item title")])
     (example "catalogue-list" nil
              (contents/cart-list ["Item title" "Another title"]))]]))

(defroutes guide-routes
  (GET "/guide" [] (guide-page)))
