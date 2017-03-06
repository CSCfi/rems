(ns rems.contents
  (:require [hiccup.element :refer [link-to image]]
            [rems.cart :as cart]
            [rems.context :as context]
            [rems.db.core :as db]))

(defn login [context]
  [:div.jumbotron
   [:h2 "Login"]
   [:p "Login by using your Haka credentials"]
   (link-to (str context "/Shibboleth.sso/Login") (image {:class "login-btn"} "/img/haka_landscape_large.gif"))])

(defn about []
  [:p "this is the story of rems... work in progress"])

;; TODO duplication between cart and catalogue to be factored out

(defn cart-item [item]
  [:tr
   [:td {:data-th "Resource in cart"} item]
   [:td {:data-th ""}]])

(defn cart-list [items]
  [:table.ctlg-table
   [:tr
    [:th "Resource in cart"]
    [:th ""]]
   (for [item (sort-by :title items)]
     (cart-item item))])

(defn catalogue-item [item]
  [:tr
   [:td {:data-th "Resource"} (:title item)]
   [:td {:data-th ""} (cart/add-to-cart-button (:title item))]])

(defn catalogue-list [items]
  [:table.ctlg-table
   [:tr
    [:th "Resource"]
    [:th ""]]
   (for [item (sort-by :title items)]
     (catalogue-item item))])

(defn catalogue []
  (list
   (cart-list context/*cart*)
   (catalogue-list (db/get-catalogue-items))))
