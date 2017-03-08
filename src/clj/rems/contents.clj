(ns rems.contents
  (:require [hiccup.element :refer [link-to image]]
            [rems.cart :as cart]
            [rems.text :refer :all]
            [rems.db.core :as db]))

(defn login [context]
  [:div.jumbotron
   [:h2 (text :t.login/title)]
   [:p (text :t.login/text)]
   (link-to (str context "/Shibboleth.sso/Login") (image {:class "login-btn"} "/img/haka_landscape_large.gif"))])

(defn about []
  [:p (text :t.about/text)])

;; TODO duplication between cart and catalogue to be factored out

(defn cart-item [item]
  [:tr
   [:td {:data-th (text :t.cart/header)} (:title item)]
   [:td {:data-th ""} (cart/remove-from-cart-button item)]])

(defn cart-list [items]
  (when-not (empty? items)
    [:table.rems-table
     [:tr
      [:th (text :t.cart/header)]
      [:th ""]]
     (for [item (sort-by :title items)]
       (cart-item item))]))

(defn urn-catalogue-item? [{:keys [resid]}]
  (and resid (.startsWith resid "http://urn.fi")))

(defn catalogue-item [item]
  (let [resid (:resid item)
        title (:title item)
        component (if (urn-catalogue-item? item)
                    [:a {:href resid :target :_blank} title]
                    title)]
    [:tr
     [:td {:data-th (text :t.catalogue/header)} component]
     [:td {:data-th ""} (cart/add-to-cart-button item)]]))

(defn catalogue-list [items]
  [:table.rems-table
   [:tr
    [:th (text :t.catalogue/header)]
    [:th ""]]
   (for [item (sort-by :title items)]
     (catalogue-item item))])

(defn catalogue []
  (list
   (cart-list (cart/get-cart-items))
   (catalogue-list (db/get-catalogue-items))))
