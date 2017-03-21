(ns rems.routes.guide
  (:require [rems.example :refer [example]]
            [rems.layout :as layout]
            [rems.context :as context]
            [rems.catalogue :as catalogue]
            [rems.cart :as cart]
            [rems.contents :as contents]
            [rems.form :as form]
            [rems.applications :as applications]
            [rems.language-switcher :refer [language-switcher]]
            [hiccup.core :as h]
            [compojure.core :refer [defroutes GET]]
            [rems.locales :as locales]
            [taoensso.tempura :as tempura :refer [tr]]
            [rems.db.core :as db]))

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
       (example "" (color-boxes))

       [:h2 "Layout components"]
       (layout/guide)

       [:h2 "Catalogue components"]
       (catalogue/guide)

       [:h2 "Cart components"]
       (cart/guide)

       [:h2 "Applications list"]
       (example "applications"
                (applications/applications
                 [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid 2}
                  {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid 4}]))

       [:h2 "Forms"]
       (example "field of type \"text\""
                [:form
                 (form/field {:type "text" :title "Title" :inputprompt "prompt"})])
       (example "field of type \"texta\""
                [:form
                 (form/field {:type "texta" :title "Title" :inputprompt "prompt"})])
       (example "field of type \"label\""
                [:form
                 (form/field {:type "label" :title "Lorem ipsum dolor sit amet"})])
       (example "field of unsupported type"
                [:form
                 (form/field {:type "unsupported" :title "Title" :inputprompt "prompt"})])
       (example "form"
                (form/form {:title "Form title"
                            :items [{:type "text" :title "Field 1" :inputprompt "prompt 1"}
                                    {:type "label" :title "Please input your wishes below."}
                                    {:type "texta" :title "Field 2" :inputprompt "prompt 2"}
                                    {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]}))
       (example "applied form"
                (form/form {:title "Form title"
                            :state "applied"
                            :items [{:type "text" :title "Field 1" :inputprompt "prompt 1"}
                                    {:type "label" :title "Please input your wishes below."}
                                    {:type "texta" :title "Field 2" :inputprompt "prompt 2"}
                                    {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]}))

       [:h2 "Misc components"]
       (example "login" (contents/login "/"))
       (example "about" (contents/about))]])))

(defroutes guide-routes
  (GET "/guide" [] (guide-page)))
