(ns rems.catalogue
  (:require [hiccup.element :refer [link-to image]]
            [rems.cart :as cart]
            [rems.form :as form]
            [rems.text :refer :all]
            [rems.db.catalogue :refer [get-localized-catalogue-items
                                       get-catalogue-item-title]]))

(defn urn-catalogue-item? [{:keys [resid]}]
  (and resid (.startsWith resid "http://urn.fi")))

(defn catalogue-item [item]
  (let [resid (:resid item)
        title (get-catalogue-item-title item)
        component (if (urn-catalogue-item? item)
                    [:a.catalogue-item-link {:href resid :target :_blank} title]
                    title)]
    [:tr
     [:td {:data-th (text :t.catalogue/header)} component]
     [:td.actions {:data-th ""} (cart/add-to-cart-button item)]]))

(defn catalogue-list [items]
  [:table.rems-table
   [:tr
    [:th (text :t.catalogue/header)]
    [:th ""]]
   (for [item (sort-by get-catalogue-item-title items)]
     (catalogue-item item))])

(defn catalogue []
  (list
   (cart/cart-list (cart/get-cart-items))
   (catalogue-list (get-localized-catalogue-items))))
