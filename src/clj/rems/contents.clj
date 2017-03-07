(ns rems.contents
  (:require [hiccup.element :refer [link-to image]]
            [rems.cart :as cart]
            [rems.context :as context]
            [rems.db.core :as db]))

(defn login [context]
  [:div.jumbotron
   [:h2 (context/*tempura* [:login/title])]
   [:p (context/*tempura* [:login/text])]
   (link-to (str context "/Shibboleth.sso/Login") (image {:class "login-btn"} "/img/haka_landscape_large.gif"))])

(defn about []
  [:p (context/*tempura* [:about/text])])

;; TODO duplication between cart and catalogue to be factored out

(defn cart-item [item]
  [:tr
   [:td {:data-th (context/*tempura* [:cart/header])} (:title item)]
   [:td {:data-th ""}]])

(defn cart-list [items]
  (when-not (empty? items)
    [:table.ctlg-table
     [:tr
      [:th (context/*tempura* [:cart/header])]
      [:th ""]]
     (for [item (sort-by :title items)]
       (cart-item item))]))

(defn catalogue-item [item]
  [:tr
   [:td {:data-th (context/*tempura* [:catalogue/header])} (:title item)]
   [:td {:data-th ""} (cart/add-to-cart-button item)]])

(defn catalogue-list [items]
  [:table.ctlg-table
   [:tr
    [:th (context/*tempura* [:catalogue/header])]
    [:th ""]]
   (for [item (sort-by :title items)]
     (catalogue-item item))])

(defn catalogue []
  (list
   (cart-list (cart/get-cart-items))
   (catalogue-list (db/get-catalogue-items))))
