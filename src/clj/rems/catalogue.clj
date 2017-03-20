(ns rems.catalogue
  (:require [hiccup.element :refer [link-to image]]
            [rems.cart :as cart]
            [rems.form :as form]
            [rems.text :refer :all]
            [rems.context :as context]
            [rems.db.catalogue :refer [get-localized-catalogue-items
                                       get-catalogue-item-title]]))

;; TODO duplication between cart and catalogue to be factored out

(defn cart-item [item]
  [:tr
   [:td {:data-th ""} (get-catalogue-item-title item)]
   [:td.actions {:data-th ""}
    (cart/apply-button item)
    (cart/remove-from-cart-button item)]])

(defn cart-list [items]
  (when-not (empty? items)
    [:div.outer-cart
     [:div.inner-cart
      [:div.cart-title
       [:i.fa.fa-shopping-cart]
       [:span (text :t.cart/header)]]
      [:table.rems-table.cart
       (for [item (sort-by get-catalogue-item-title items)]
         (cart-item item))]
      [:div.full.actions (cart/checkout-cart-button)]]]))

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
   (cart-list (cart/get-cart-items))
   (catalogue-list (get-localized-catalogue-items))))
