(ns rems.catalogue
  (:require [ajax.core :refer [GET]]
            [re-frame.core :as rf]
            [rems.atoms :refer [external-link]]
            [rems.cart :as cart]
            [rems.db.catalogue :refer [disabled-catalogue-item?
                                       get-catalogue-item-title
                                       urn-catalogue-item-link
                                       urn-catalogue-item?]]
            [rems.guide-functions]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [redirect-when-unauthorized]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-db
 ::set-sort-order
 (fn [db [_ order]]
   (assoc db ::sort-order order)))

(rf/reg-sub
 ::sort-order
 (fn [db _]
   (get db ::sort-order [:name :asc])))

(rf/reg-event-db
 ::catalogue
 (fn [db [_ catalogue]]
   (assoc db ::catalogue catalogue)))

(rf/reg-sub
 ::catalogue
 (fn [db _]
   (::catalogue db)))

(defn- catalogue-item-title [item language config]
  (let [title (get-catalogue-item-title item language)]
    (if (urn-catalogue-item? item)
      [:a.catalogue-item-link {:href (urn-catalogue-item-link item config) :target :_blank} title " " [external-link]]
      [:span title])))

(defn- catalogue-columns [lang config]
  {:name {:header #(text :t.catalogue/header)
          :value (fn [item] [catalogue-item-title item lang config])
          :sort-value #(get-catalogue-item-title % lang)}
   :cart {:value (fn [i] [cart/add-to-cart-button i])
          :sortable? false
          :class "commands"}})

(defn- catalogue-list
  [items language sort-order config]
  [table/component
   (catalogue-columns language config) [:name :cart]
   sort-order #(rf/dispatch [::set-sort-order %])
   :id
   (filter (complement disabled-catalogue-item?) items)
   {:class "catalogue"}])

(defn- fetch-catalogue []
  (GET "/api/catalogue/" {:handler #(rf/dispatch [::catalogue %])
                          :error-handler redirect-when-unauthorized
                          :response-format :json
                          :keywords? true}))

(defn catalogue-page []
  (fetch-catalogue)
  (let [catalogue @(rf/subscribe [::catalogue])
        language @(rf/subscribe [:language])
        sort-order @(rf/subscribe [::sort-order])
        config @(rf/subscribe [:rems.config/config])]
    [:div
     [cart/cart-list-container language]
     [catalogue-list catalogue language sort-order config]]))

(defn guide []
  [:div
   (component-info catalogue-item-title)
   (example "catalogue-item-title"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Item title"} nil]]]]])
   (example "catalogue-item-title linked to urn.fi"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Item title" :resid "urn:nbn:fi:lb-201403262"} nil nil]]]]])
   (example "catalogue-item-title linked to example.org"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Item title" :resid "urn:nbn:fi:lb-201403262"} nil {:urn-prefix "http://example.org/"}]]]]])
   (example "catalogue-item-title in Finnish with localizations"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}} :en]]]]])
   (example "catalogue-item-title in English with localizations"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Not used when there are localizations" :localizations {:fi {:title "Suomenkielinen title"} :en {:title "English title"}}} :fi]]]]])

   (component-info catalogue-list)
   (example "catalogue-list empty"
            [catalogue-list [] nil [:name :asc]])
   (example "catalogue-list with two items"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil [:name :asc]])
   (example "catalogue-list with two items in reverse order"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil [:name :desc]])
   (example "catalogue-list with three items, of which second is disabled"
            [catalogue-list [{:title "Item 1"} {:title "Item 2 is disabled and should not be shown" :state "disabled"} {:title "Item 3"}] nil [:name :asc]])])
