(ns rems.catalogue
  (:require [compojure.core :refer [GET defroutes routes]]
            [hiccup.element :refer [link-to image]]
            [rems.context :as context]
            [rems.cart :as cart]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.text :refer :all]
            [rems.db.catalogue :refer [disabled-catalogue-item?
                                       get-catalogue-item-title
                                       get-localized-catalogue-items]]))

(defn- urn-catalogue-item? [{:keys [resid]}]
  (and resid (.startsWith resid "http://urn.fi")))

(defn- catalogue-item [item]
  (let [resid (:resid item)
        title (get-catalogue-item-title item)
        component (if (urn-catalogue-item? item)
                    [:a.catalogue-item-link {:href resid :target :_blank} title " " (layout/external-link)]
                    title)]
    [:tr
     [:td {:data-th (text :t.catalogue/header)} component]
     [:td.commands {:data-th ""} (cart/add-to-cart-button item)]]))

(defn- catalogue-list [items]
  [:table.rems-table.catalogue
   [:tr
    [:th (text :t.catalogue/header)]
    [:th ""]]
   (for [item (sort-by get-catalogue-item-title (remove disabled-catalogue-item? items))]
     (catalogue-item item))])

(defn- catalogue []
  (list
   (cart/cart-list (cart/get-cart-items))
   (catalogue-list (get-localized-catalogue-items))))
