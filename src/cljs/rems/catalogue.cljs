(ns rems.catalogue
  (:require [clojure.string :as s]
            [rems.cart :as cart]
            ;; [rems.form :as form]
            [rems.db.catalogue :refer [urn-catalogue-item? get-catalogue-item-title disabled-catalogue-item?]]
            [rems.guide-functions]
            [rems.text :refer [text]]
            [rems.atoms :refer [external-link]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn catalogue-item
  "Single catalogue item"
  [item language]
  (let [resid (:resid item)
        title (get-catalogue-item-title item language)
        component (if (urn-catalogue-item? item)
                    [:a.catalogue-item-link {:href resid :target :_blank} title " " [external-link]]
                    title)]
    [:tr
     [:td {:data-th (text :t.catalogue/header)} component]
     [:td.commands {:data-th ""} [cart/add-to-cart-button item]]]))

(defn- catalogue-list
  "List of catalogue items"
  [items language]
  [:table.rems-table.catalogue
   (into [:tbody
          [:tr
           [:th (text :t.catalogue/header)]
           [:th ""]]]
         (for [item (sort-by #(get-catalogue-item-title % language)
                             (remove disabled-catalogue-item? items))]
           [catalogue-item item language]))])

(defn catalogue-page []
  [:div
   ;; TODO fetch data
   [cart/cart-list [] #_(cart/get-cart-items)]
   [catalogue-list [] :en #_(get-localized-catalogue-items)]])

(defn guide []
  [:div
   (component-info catalogue-item)
   (example "catalogue-item"
            [:table.rems-table
             [:tbody
              [catalogue-item {:title "Item title"} nil]]])
   (example "catalogue-item linked to urn.fi"
            [:table.rems-table
             [:tbody
              [catalogue-item {:title "Item title" :resid "http://urn.fi/urn:nbn:fi:lb-201403262"} nil]]])
   (example "catalogue-item in Finnish with localizations"
            [:table.rems-table
             [:tbody
              [catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}} :en]]])
   (example "catalogue-item in English with localizations"
            [:table.rems-table
             [:tbody
              [catalogue-item {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}} :fi]]])

   (component-info catalogue-list)
   (example "catalogue-list empty"
            [catalogue-list [] nil])
   (example "catalogue-list with two items"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil])
    (example "catalogue-list with three items, of which second is disabled"
             [catalogue-list [{:title "Item 1"} {:title "Item 2 is disabled and should not be shown" :state "disabled"} {:title "Item 3"}] nil])
   ])
