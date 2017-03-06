(ns rems.contents
  (:require [hiccup.element :refer [link-to image]]
            [rems.db.core :as db]))

(defn login [context]
  [:div.jumbotron
   [:h2 "Login"]
   [:p "Login by using your Haka credentials"]
   (link-to (str context "/Shibboleth.sso/Login") (image {:class "login-btn"} "/img/haka_landscape_large.gif"))])

(defn about []
  [:p "this is the story of rems... work in progress"])

(defn catalogue-item [item]
  [:tr
   [:td {:data-th "Resource"} (:title item)]
   [:td {:data-th ""} [:div.btn.btn-primary "Add to cart"]]])

(defn catalogue []
  [:table.ctlg-table
   [:tr
    [:th "Resource"]
    [:th ""]]
   (for [item (sort-by :title (db/get-catalogue-items))]
     (catalogue-item item))])
