(ns rems.catalogue
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [external-link]]
            [rems.cart :as cart]
            [rems.db.catalogue :refer [disabled-catalogue-item?
                                       get-catalogue-item-title
                                       urn-catalogue-item-link
                                       urn-catalogue-item?]]
            [rems.guide-functions]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-db
  ::set-sorting
  (fn [db [_ sorting]]
    (println ::set-sorting sorting)
    (assoc db ::sorting2 sorting)))

(rf/reg-sub
  ::sorting
  (fn [db _]
    (or (::sorting2 db)
        {:sort-column :name
         :sort-order  :asc})))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading?))))

(rf/reg-sub
 ::catalogue
 (fn [db _]
   (::catalogue db)))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

(rf/reg-event-fx
 ::start-fetch-catalogue
 (fn [{:keys [db]} _]
   {:db (assoc db ::loading? true)
    ::fetch-catalogue []}))

(defn- fetch-catalogue []
  (fetch "/api/catalogue/" {:handler #(rf/dispatch [::fetch-catalogue-result %])}))

(rf/reg-fx
 ::fetch-catalogue
 (fn [_]
   (fetch-catalogue)))

(defn- catalogue-item-title [item language]
  [:span (get-catalogue-item-title item language)])

(defn- catalogue-item-more-info [item config]
  (when (urn-catalogue-item? item)
    [:a.btn.btn-secondary {:href (urn-catalogue-item-link item config) :target :_blank}
     (text :t.catalogue/more-info) " " [external-link]]))

(defn- catalogue-columns [lang config]
  {:name     {:header     #(text :t.catalogue/header)
              :value      (fn [item] [catalogue-item-title item lang])
              :sort-value #(get-catalogue-item-title % lang)}
   :commands {:values    (fn [item] [[catalogue-item-more-info item config]
                                     [cart/add-to-cart-button item]])
              :sortable? false
              :filterable? false}})

(defn- catalogue-list
  [items language sorting config]
  [table/component
   (catalogue-columns language config) [:name :commands]
   sorting
   #(rf/dispatch [::set-sorting %])
   :id
   (filter (complement disabled-catalogue-item?) items)
   {:class "catalogue"}])

(defn catalogue-page []
  (let [catalogue (rf/subscribe [::catalogue])
        loading? (rf/subscribe [::loading?])
        language (rf/subscribe [:language])
        sorting (rf/subscribe [::sorting])
        config (rf/subscribe [:rems.config/config])]
    (fn []
      [:div
       [:h2 (text :t.catalogue/catalogue)]
       (if @loading?
         [spinner/big]
         [:div
          [cart/cart-list-container @language]
          [catalogue-list @catalogue @language @sorting @config]])])))

(defn guide []
  [:div
   (component-info catalogue-item-title)
   (example "catalogue-item-title"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Item title"} nil]]]]])
   (example "catalogue-item-title in Finnish with localizations"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Not used when there are localizations"
                                       :localizations {:fi {:title "Suomenkielinen title"}
                                                       :en {:title "English title"}}} :en]]]]])
   (example "catalogue-item-title in English with localizations"
            [:table.rems-table
             [:tbody
              [:tr
               [:td
                [catalogue-item-title {:title "Not used when there are localizations"
                                       :localizations {:fi {:title "Suomenkielinen title"}
                                                       :en {:title "English title"}}} :fi]]]]])

   (component-info catalogue-list)
   (example "catalogue-list empty"
            [catalogue-list [] nil {:sort-column :name, :sort-order :asc}])
   (example "catalogue-list with two items"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil {:sort-column :name, :sort-order :asc}])
   (example "catalogue-list with two items in reverse order"
            [catalogue-list [{:title "Item title"} {:title "Another title"}] nil {:sort-column :name, :sort-order :desc}])
   (example "catalogue-list with three items, of which second is disabled"
            [catalogue-list [{:title "Item 1"} {:title "Item 2 is disabled and should not be shown" :state "disabled"} {:title "Item 3"}] nil {:sort-column :name, :sort-order :asc}])
   (example "catalogue-list with item linked to urn.fi"
            [catalogue-list [{:title "Item title" :resid "urn:nbn:fi:lb-201403262"}] nil {:sort-column :name, :sort-order :asc}])
   (example "catalogue-list with item linked to example.org"
            [catalogue-list [{:title "Item title" :resid "urn:nbn:fi:lb-201403262"}] nil {:sort-column :name, :sort-order :asc} {:urn-prefix "http://example.org/"}])])
