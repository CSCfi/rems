(ns rems.routes.guide
  (:require [rems.layout :as layout]
            [rems.context :as context]
            [rems.contents :as contents]
            [rems.form :as form]
            [rems.applications :as applications]
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
  (binding [context/*root-path* "path/"
            context/*lang* :en
            context/*tempura* (partial tr locales/tconfig [:en])]
    (h/html
     [:head
      [:link {:type "text/css" :rel "stylesheet" :href "/assets/bootstrap/css/bootstrap.min.css"}]
      [:link {:type "text/css" :rel "stylesheet" :href "/assets/font-awesome/css/font-awesome.min.css"}]
      [:link {:type "text/css" :rel "stylesheet" :href "/css/screen.css"}]]
     [:body
      [:div.example-page
       [:h1 "Component Guide"]

       [:h2 "Colors"]
       (example "" nil (color-boxes))

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
                 (binding [context/*lang* :fi
                           context/*tempura* (partial tr locales/tconfig [:fi])]
                   (contents/catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}}))])
       (example "catalogue-item in English with localizations" nil
                [:table.rems-table
                 (binding [context/*lang* :en
                           context/*tempura* (partial tr locales/tconfig [:en])]
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

       [:h2 "Applications list"]
       (example "applications" nil
                (applications/applications
                 [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid 2}
                  {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid 4}]))

       [:h2 "Forms"]
       (example "field of type \"text\"" nil
                [:form
                 (form/field {:type "text" :title "Title" :inputprompt "prompt"})])
       (example "field of type \"texta\"" nil
                [:form
                 (form/field {:type "texta" :title "Title" :inputprompt "prompt"})])
       (example "field of type \"label\"" nil
                [:form
                 (form/field {:type "label" :title "Lorem ipsum dolor sit amet"})])
       (example "field of unsupported type" nil
                [:form
                 (form/field {:type "unsupported" :title "Title" :inputprompt "prompt"})])
       (example "form" nil
                (form/form {:title "Form title"
                            :items [{:type "text" :title "Field 1" :inputprompt "prompt 1"}
                                    {:type "label" :title "Please input your wishes below."}
                                    {:type "texta" :title "Field 2" :inputprompt "prompt 2"}
                                    {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]}))

       [:h2 "Misc components"]
       (example "login" nil (contents/login "/"))
       (example "about" nil (contents/about))
       (example "logo" nil (layout/logo))
       (example "error-content" nil (layout/error-content {:status 123 :title "Error title" :message "Error message"}))
       ]])))

(defroutes guide-routes
  (GET "/guide" [] (guide-page)))
