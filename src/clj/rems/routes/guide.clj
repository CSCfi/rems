(ns rems.routes.guide
  (:require [rems.layout :as layout]
            [rems.context :as context]
            [rems.contents :as contents]
            [rems.form :as form]
            [rems.language-switcher :refer [language-switcher]]
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
       (example "nav-link" nil
                (layout/nav-link "example/path" "link text"))
       (example "nav-link active" nil
                (layout/nav-link "example/path" "link text" "page-name" "page-name"))
       (example "nav-item" nil
                (layout/nav-link "example/path" "link text" "page-name" "li-name"))
       (example "language-switcher" nil
                (language-switcher))
       (example "navbar guest" nil
                (layout/navbar "example-page" nil))
       (example "navbar for logged-in user" nil
                (layout/navbar "example-page" g-user))
       (example "footer" nil
                (layout/footer))


       [:h2 "Catalogue components"]
       (example "catalogue-item" nil
                [:table.rems-table
                 (contents/catalogue-item {:title "Item title"})])
       (example "catalogue-item linked to urn.fi" nil
                [:table.rems-table
                 (contents/catalogue-item {:title "Item title" :resid "http://urn.fi/urn:nbn:fi:lb-201403262"})])
       (example "catalogue-item in Finnish with localizations" nil
                [:table.rems-table
                 (binding [context/*lang* :fi]
                   (contents/catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}}))])
       (example "catalogue-item in English with localizations" nil
                [:table.rems-table
                 (binding [context/*lang* :en]
                   (contents/catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}}))])

       (example "catalogue-list empty" nil
                (contents/catalogue-list []))
       (example "catalogue-list with two items" nil
                (contents/catalogue-list [{:title "Item title"} {:title "Another title"}]))


       [:h2 "Cart components"]
       (example "cart-item" nil
                [:table.rems-table.cart
                 (contents/cart-item {:title "Item title"})])
       (example "cart-list empty" nil
                (contents/cart-list []))
       (example "cart-list with two items" nil
                (contents/cart-list [{:title "Item title"} {:title "Another title"}]))

       [:h2 "Forms"]
       (example "\"text\" field" nil
                [:form
                 (form/field {:type "text" :title "Title" :order 1 :inputprompt "prompt"})])
       (example "\"texta\" field" nil
                [:form
                 (form/field {:type "texta" :title "Title" :order 1 :inputprompt "prompt"})])
       (example "unsupported field" nil
                [:form
                 (form/field {:type "unsupported" :title "Title" :order 1 :inputprompt "prompt"})])
       (example "form" nil
                (form/form {:formtitle "Form title"}
                           [{:type "text" :title "Field 1" :order 1 :inputprompt "prompt 1"}
                            {:type "texta" :title "Field 2" :order 2 :inputprompt "prompt 2"}
                            {:type "unsupported" :title "Field 3" :order 3 :inputprompt "prompt 3"}]))


       [:h2 "Misc components"]
       (example "login" nil (contents/login "/"))
       (example "about" nil (contents/about))
       (example "logo" nil (layout/logo))
       (example "error-content" nil (layout/error-content {:status 123 :title "Error title" :message "Error message"}))
       ]])))

(defroutes guide-routes
  (GET "/guide" [] (guide-page)))
