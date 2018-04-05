(ns rems.catalogue
  (:require [ajax.core :refer [GET]]
            [clojure.string :as s]
            [re-frame.core :as rf]
            [rems.cart :as cart]
            [rems.db.catalogue :refer [urn-catalogue-item? get-catalogue-item-title disabled-catalogue-item?]]
            [rems.guide-functions]
            [rems.text :refer [text]]
            [rems.atoms :refer [external-link]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-db
 ::sort-by
 (fn [db [_ _]]
   (let [sort-order (if (= (::sort-order db) :asc)
                      :desc
                      :asc)]
     (assoc db ::sort-order sort-order))))

(rf/reg-sub
 ::sort-order
 (fn [db _]
   (::sort-order db)))

(rf/reg-event-db
 ::catalogue
 (fn [db [_ catalogue]]
   (assoc db ::catalogue catalogue)))

(rf/reg-sub
 ::catalogue
 (fn [db _]
   (::catalogue db)))

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
  [items language sort-order]
  (let [sorted (sort-by #(get-catalogue-item-title % language)
                        (remove disabled-catalogue-item? items))
        sorted-items (if (= sort-order :desc)
                       (reverse sorted)
                       sorted)]
    [:table.rems-table.catalogue
     (into [:tbody
            [:tr
             [:th {:on-click #(rf/dispatch [::sort-by])}
              (str (text :t.catalogue/header) " ")
              [:i {:class (if (= sort-order :desc)
                            "fa fa-arrow-up"
                            "fa fa-arrow-down")}]]
             [:th ""]]]
           (doall (for [item sorted-items]
                    [catalogue-item item language])))]))

(defn- fetch-catalogue []
  (GET "/api/catalogue/" {:handler #(rf/dispatch [::catalogue %])
                          :response-format :json
                          :keywords? true}))

(defn catalogue-page []
  (fetch-catalogue)
  (let [catalogue @(rf/subscribe [::catalogue])
        language @(rf/subscribe [:language])
        sort-order @(rf/subscribe [::sort-order])]
    [:div
     [cart/cart-list-container language]
     [catalogue-list catalogue language sort-order]]))

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
            [catalogue-list [] nil :asc])
   (example "catalogue-list with two items"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil :asc])
   (example "catalogue-list with two items in reverse order"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil :desc])
   (example "catalogue-list with three items, of which second is disabled"
            [catalogue-list [{:title "Item 1"} {:title "Item 2 is disabled and should not be shown" :state "disabled"} {:title "Item 3"}] nil :asc])])
