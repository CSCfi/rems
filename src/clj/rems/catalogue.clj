(ns rems.catalogue
  (:require [hiccup.element :refer [link-to image]]
            [taoensso.tempura :refer [tr]]
            [rems.locales :as locales]
            [rems.context :as context]
            [rems.cart :as cart]
            [rems.form :as form]
            [rems.example :refer :all]
            [rems.text :refer :all]
            [rems.db.catalogue :refer [get-localized-catalogue-items
                                       get-catalogue-item-title]]))

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
     [:td.actions {:data-th ""} (cart/add-to-cart-button item)]]))

(defn- catalogue-list [items]
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

(defn guide []
  (list
   (example "catalogue-item"
            [:table.rems-table
             (catalogue-item {:title "Item title"})])
   (example "catalogue-item linked to urn.fi"
            [:table.rems-table
             (catalogue-item {:title "Item title" :resid "http://urn.fi/urn:nbn:fi:lb-201403262"})])
   ;; TODO write helper for overriding language
   (example "catalogue-item in Finnish with localizations"
            [:table.rems-table
             (binding [context/*lang* :fi
                       context/*tempura* (partial tr locales/tconfig [:fi])]
               (catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}}))])
   (example "catalogue-item in English with localizations"
            [:table.rems-table
             (binding [context/*lang* :en
                       context/*tempura* (partial tr locales/tconfig [:en])]
               (catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}}))])

   (example "catalogue-list empty"
            (catalogue-list []))
   (example "catalogue-list with two items"
            (catalogue-list [{:title "Item title"} {:title "Another title"}]))))
