(ns rems.cart
  (:require [cljs.tools.reader.edn :as edn]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text text-format get-localized-title]]))

(rf/reg-sub
 ::cart
 (fn [db]
   (::cart db)))

(rf/reg-event-db
 ::add-item
 (fn [db [_ item]]
   (if (contains? (set (map :id (::cart db))) (:id item))
     db
     (update db ::cart conj item))))

(rf/reg-event-db
 ::remove-item
 (fn [db [_ item-id]]
   (update db ::cart #(remove (comp #{item-id} :id) %))))

(defn add-to-cart-button
  "Hiccup fragment that contains a button that adds the given item to the cart"
  [item language]
  [:button.btn.btn-primary.add-to-cart
   {:type :button
    :on-click #(rf/dispatch [::add-item item])
    :aria-label (str (text :t.cart/add) ": " (get-localized-title item language))}
   (text :t.cart/add)])

(defn remove-from-cart-button
  "Hiccup fragment that contains a button that removes the given item from the cart"
  [item language]
  [:button.btn.btn-secondary.remove-from-cart
   {:type :button
    :on-click #(rf/dispatch [::remove-item (:id item)])
    :aria-label (str (text :t.cart/remove)
                     ": "
                     (get-localized-title item language))}
   (text :t.cart/remove)])

;; TODO make util for other pages to use?
(defn parse-items [items-string]
  (->> (str/split items-string #",")
       (mapv edn/read-string)))

(defn- apply-button [items language]
  [atoms/link {:class "btn btn-primary apply-for-catalogue-items"
               :aria-label (str (text :t.cart/apply)
                                ": "
                                (->> items
                                     (map #(get-localized-title % language))
                                     (str/join ", ")))}
   (str "/application?items=" (str/join "," (sort (map :id items))))
   (text :t.cart/apply)])

(defn- item-view [item language apply-button?]
  [:tr.cart-item
   [:td.title (get-localized-title item language)]
   [:td.commands
    [:div.commands.justify-content-end
     [remove-from-cart-button item language]
     (when apply-button? [apply-button [item] language])]]])

(defn- bundle-view [items language]
  (let [many-items? (< 1 (count items))]
    (into [:tbody.cart-bundle]
          (concat (map (fn [item]
                         [item-view item language (not many-items?)])
                       items)
                  (when many-items?
                    [[:tr [:td.commands.text-right {:col-span 2}
                           (text-format :t.cart/apply-for-bundle (count items))
                           [:span.mr-3]
                           [apply-button items language]]]])))))

(defn cart-list
  "List of shopping cart items"
  [items language]
  [:div.mt-5
   (text :t.cart/intro)
   [:div.outer-cart.mb-3
    [:div.inner-cart
     [:div.cart-title.pt-3.h5 {:role "status"
                               :aria-live "polite"
                               :aria-atomic true}
      [:i.fa.fa-shopping-cart]
      [:span (text-format :t.cart/header (count items))]]
     (into [:table.rems-table.cart]
           (for [group (vals (into (sorted-map)
                                   (group-by :wfid items)))]
             [bundle-view (sort-by get-localized-title group) language]))]]])

(defn cart-list-container []
  (let [language @(rf/subscribe [:language])
        cart @(rf/subscribe [::cart])]
    [cart-list cart language]))

(defn guide []
  [:div
   (component-info item-view)
   (example "item-view, single"
            [:code
             [:table.rems-table.cart
              [:tbody
               [item-view {:localizations {:en {:title "Item title"}}} nil true]]]])
   (example "item-view, one of many has no apply button"
            [:code
             [:table.rems-table.cart
              [:tbody
               [item-view {:localizations {:en {:title "Item title"}}} nil false]]]])

   (component-info bundle-view)
   (example "bundle-view"
            [:code
             [:table.rems-table.cart
              [bundle-view [{:localizations {:en {:title "Item title 1"}}}
                            {:localizations {:en {:title "Item title 2"}}}
                            {:localizations {:en {:title "Item title 3"}}}] nil]]])

   (component-info cart-list)
   (example "cart-list empty"
            [cart-list [] nil])
   (example "cart-list with two items of different workflow"
            [cart-list [{:localizations {:en {:title "Item title"}} :wfid 1}
                        {:localizations {:en {:title "Another title"}} :wfid 2}] nil])
   (example "cart-list with three items of same workflow and two of different"
            [cart-list [{:localizations {:en {:title "First title"}} :wfid 2}
                        {:localizations {:en {:title "Second title"}} :wfid 1}
                        {:localizations {:en {:title "Third title"}} :wfid 1}
                        {:localizations {:en {:title "Fourth title"}} :wfid 1}
                        {:localizations {:en {:title "Fifth title"}} :wfid 3}] nil])
   (example "cart-list with five items of same workflow but of two different forms"
            [cart-list [{:localizations {:en {:title "First form"}} :wfid 1 :formid 1}
                        {:localizations {:en {:title "Second form"}} :wfid 1 :formid 2}
                        {:localizations {:en {:title "First form"}} :wfid 1 :formid 1}
                        {:localizations {:en {:title "Second form"}} :wfid 1 :formid 2}
                        {:localizations {:en {:title "First form"}} :wfid 1 :formid 1}] nil])])
