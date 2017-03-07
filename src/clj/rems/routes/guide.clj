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

(defn example [name description content]
  [:div.example
   [:h3 name]
   (when description
     description)
   [:div.example-content content
    [:div.example-content-end]]])

(defn guide-page []
  (binding [context/*root-path* "path/"
            context/*tempura* (partial tr locales/tconfig [:fi])]
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
                (layout/primary-nav "page-name" g-user))
       (example "secondary-nav" nil
                (layout/secondary-nav g-user))
       (example "navbar" nil
                (layout/navbar "example-page" g-user))
       (example "footer" nil
                (layout/footer))


       [:h2 "Catalogue components"]
       (example "catalogue-item" nil
                [:table.ctlg-table
                 (contents/catalogue-item {:title "Item title"})])
       (example "catalogue-item linked to urn.fi" nil
                [:table.ctlg-table
                 (contents/catalogue-item {:title "Item title" :resid "http://urn.fi/urn:nbn:fi:lb-201403262"})])
       (example "catalogue-list empty" nil
                (contents/catalogue-list []))
       (example "catalogue-list with two items" nil
                (contents/catalogue-list [{:title "Item title"} {:title "Another title"}]))


       [:h2 "Cart components"]
       (example "cart-item" nil
                [:table.ctlg-table
                 (contents/cart-item {:title "Item title"})])
       (example "cart-list empty" nil
                (contents/cart-list []))
       (example "cart-list with two items" nil
                (contents/cart-list [{:title "Item title"} {:title "Another title"}]))


       [:h2 "Misc components"]
       (example "login" nil (contents/login "/"))
       (example "about" nil (contents/about))
       (example "logo" nil (layout/logo))
       (example "error-content" nil (layout/error-content {:status 123 :title "Error title" :message "Error message"}))
       ]])))

(defroutes guide-routes
  (GET "/guide" [] (guide-page)))
