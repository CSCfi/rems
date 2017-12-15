(ns rems.catalogue
  (:require [compojure.core :refer [GET defroutes routes]]
            [hiccup.element :refer [link-to image]]
            [rems.context :as context]
            [rems.cart :as cart]
            [rems.form :as form]
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
                    [:a.catalogue-item-link {:href resid :target :_blank} title]
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

(defn- catalogue-page []
  (layout/render
    "catalogue" (catalogue)))

(defroutes catalogue-routes
  (GET "/catalogue" [] (catalogue-page)))

(defn guide []
  (list
   (component-info catalogue-item)
   (example "catalogue-item"
            [:table.rems-table
             (catalogue-item {:title "Item title"})])
   (example "catalogue-item linked to urn.fi"
            [:table.rems-table
             (catalogue-item {:title "Item title" :resid "http://urn.fi/urn:nbn:fi:lb-201403262"})])
   (example "catalogue-item in Finnish with localizations"
            [:table.rems-table
             (with-language :fi
               (catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}}))])
   (example "catalogue-item in English with localizations"
            [:table.rems-table
             (with-language :en
               (catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}}))])

   (component-info catalogue-list)
   (example "catalogue-list empty"
            (catalogue-list []))
   (example "catalogue-list with two items"
            (catalogue-list [{:title "Item title"} {:title "Another title"}]))
   (example "catalogue-list with three items, one of which second is disabled"
            (catalogue-list [{:title "Item 1"} {:title "Item 2 is disabled and should not be shown" :state "disabled"} {:title "Item 3"}]))))
