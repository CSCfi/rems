(ns rems.layout
  (:require [compojure.response]
            [hiccup.element :refer [link-to]]
            [hiccup.page :refer [html5 include-css include-js]]
            [markdown.core :refer [md-to-html-string]]
            [ring.util.http-response :refer [content-type ok]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]])
  (:import compojure.response.Renderable))


(declare ^:dynamic *app-context*)

(defn- localized-nav [{tr :tempura/tr}]
  {:home (tr [:navigation/home])
   :login (tr [:navigation/login])
   :logout (tr [:navigation/logout])
   :about (tr [:navigation/about])
   :catalogue (tr [:navigation/catalogue])})

(defn primary-nav
  [user context]
  [:ul.nav.navbar-nav
   [:li.nav-item
    (link-to {:class "nav-link"} (str context "/") "Home")]
   [:li.nav-item
    (link-to {:class "nav-link"} (str context "/about") "About")]
   (when user
     [:li.nav-item
      (link-to {:class "nav-link"} (str context "/catalogue") "Catalogue")])])

(defn secondary-nav
  [user context]
  [:div.secondary-navigation.navbar-nav.navitem
   [:div.fa.fa-user {:style "display: inline-block"} (str user " / ")]
   [:div {:style "display: inline-block"}
    (link-to {:class "nav-link"} (str context "/logout") "Sign Out")]])

(defn navbar
  [user]
  [:nav.navbar.rems-navbar {:role "navigation"}
   [:button.navbar-toggler.hidden-sm-up {:type "button" :data-toggle "collapse" :data-target "#collapsing-navbar"} "&#9776;"]
   (let [context (if (bound? #'*app-context*) *app-context* nil)]
     [:div#collapsing-navbar.collapse.navbar-toggleable-xs
      (primary-nav user context)
      (when user
        (secondary-nav user context))
     ])])

(defn footer []
  [:article.footer-wrapper
   [:p "Powered by CSC - IT Center for Science"]])

(defn page-template
  [content user]
  (html5 [:head
          [:META {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
          [:META {:name "viewport" :content "width=device-width, initial-scale=1"}]
          [:title "Welcome to rems"]
          (include-css "/assets/bootstrap/css/bootstrap.min.css")
          (include-css "/assets/font-awesome/css/font-awesome.min.css")
          (include-css "/css/screen.css")

          [:body
           [:div.wrapper
            [:div.container (navbar user)]
            [:div.logo]
            [:div.container content]]
           [:footer (footer)]
           (include-js "/assets/jquery/jquery.min.js")
           (include-js "/assets/tether/dist/js/tether.min.js")
           (include-js "/assets/bootstrap/js/bootstrap.min.js")]]))

(deftype
  RenderableTemplate
  [content params]
  Renderable
  (render
    [this request]
    (content-type
    (ok
      (page-template content (:identity request)))
    "text/html; charset=utf-8")))

(defn render
  "renders the HTML template located relative to resources/templates"
  [content & [params]]
  (RenderableTemplate. content params))

(defn error-content
  [error-details]
  [:div.container-fluid
   [:div.row-fluid
    [:div.col-lg-12
     [:div.centering.text-center
      [:div.text-center
       [:h1
        [:span.text-danger (str "Error: " (error-details :status))]
        [:hr]
        [:h2.without-margin (error-details :title)]
        [:h4.text-danger (error-details :message)]]]]]]])

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  {:status  (:status error-details)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (render (error-content error-details))})
