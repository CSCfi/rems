(ns rems.contents
  (:require [hiccup.element :refer [link-to image]]))

(defn login [context]
  [:div.jumbotron
   [:h2 "Login"]
   [:p "Login by using your Haka credentials"]
   (link-to (str context "/Shibboleth.sso/Login") (image {:class "login-btn"} "/img/haka_landscape_large.gif"))])

(defn about []
  [:p "this is the story of rems... work in progress"])

(defn catalogue []
  [:table.ctlg-table
   [:tr
    [:th "Resource"]
    [:th ""]]
   [:tr
    [:td {:data-th "Resource"} "A"]
    [:td {:data-th ""} [:div.btn.btn-primary "Add to cart"]]]
   [:tr
    [:td {:data-th "Resource"} "B"]
    [:td {:data-th ""} [:div.btn.btn-primary "Add to cart"]]]
   [:tr
    [:td {:data-th "Resource"} "C"]
    [:td {:data-th ""} [:div.btn.btn-primary "Add to cart"]]]
   [:tr
    [:td {:data-th "Resource"} "D"]
    [:td {:data-th ""} [:div.btn.btn-primary "Add to cart"]]]
   [:tr
    [:td {:data-th "Resource"} "E"]
    [:td {:data-th ""} [:div.btn.btn-primary "Add to cart"]]]])
