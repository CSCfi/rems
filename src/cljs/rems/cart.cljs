(ns rems.cart
  (:require #_[rems.form :as form]
            [re-frame.core :as re-frame]
            [rems.util :refer [select-vals]]
            [rems.db.catalogue :refer [get-catalogue-item-title]]
            [rems.text :refer [text text-format]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;; TODO anti-forgery when submitting

(re-frame/reg-sub
 ::cart
 (fn [db]
   (::cart db)))

(re-frame/reg-event-db
 ::add-item
 (fn [db [_ item]]
   (let [cart (-> (::cart db)
                  (conj item))]
     (assoc db ::cart cart ))))

(re-frame/reg-event-db
 ::remove-item
 (fn [db [_ item]]
   (let [cart (->> (::cart db)
                  (remove (comp #{(:id item)} :id)))]
     (assoc db ::cart cart ))))

(defn add-to-cart-button
  "Hiccup fragment that contains a button that adds the given item to the cart"
  [item]
  (let [cart @(re-frame/subscribe [::cart])
        disabled? (and cart (contains? (set (map :id cart)) (:id item)))]
    [:button.btn.btn-primary
     {:type "submit"
      :disabled disabled?
      :class (if disabled? " disabled" "")
      :on-click #(re-frame/dispatch [::add-item item])}
     (text :t.cart/add)]))

(defn remove-from-cart-button
  "Hiccup fragment that contains a button that removes the given item from the cart"
  [item]
  [:button.btn.btn-secondary
   {:type "submit"
    :on-click #(re-frame/dispatch [::remove-item item])}
   (text :t.cart/remove)])

(defn- apply-button [items]
  [:a.btn.btn-primary {:href "TODO" #_(form/link-to-application items)} (text :t.cart/apply)])

(defn- item-view [item language & [apply-button?]]
  [:tr.cart-item {:class (if apply-button? "separator" "")}
   [:td.title (get-catalogue-item-title item language)]
   [:td.commands
    [remove-from-cart-button item]
    (when apply-button? [apply-button [item]])]])

(defn group-view
  "Returns a seq of items that can be applied with the same application."
  [items language]
  (if (= 1 (count items))
    [[item-view (first items) language true]]
    (concat (map #(item-view % language) items)
            [[:tr.separator [:td.commands.text-right {:col-span 2} (text-format :t.cart/apply-for-bundle (count items)) [:span.mr-3] [apply-button items]]]])))

(defn cart-list
  "List of shopping cart items"
  [items language]
  (when-not (empty? items)
    [:div.outer-cart
     [:div.inner-cart
      [:div.cart-title
       [:i.fa.fa-shopping-cart]
       [:span (text-format :t.cart/header (count items))]]
      [:table.rems-table.cart
       (into [:tbody]
             (let [key-fn #(select-vals % [:wfid :formid])]
               (apply concat
                      (for [group (vals (into (sorted-map) (group-by key-fn items)))]
                        (group-view (sort-by get-catalogue-item-title group) language)))))]]]))

(defn cart-list-container [language]
  (let [cart @(re-frame/subscribe [::cart])]
    [cart-list cart language]))

(defn guide []
  [:div
   (component-info item-view)
   (example "item-view, single"
            [:table.rems-table.cart
             [:tbody
              [item-view {:title "Item title"} nil true]]])
   (example "item-view, one of many has no apply button"
            [:table.rems-table.cart
             [:tbody
              [item-view {:title "Item title"} nil false]]])

   (component-info group-view)
   (example "group-view"
            [:table.rems-table.cart
             (into [:tbody]
                   (group-view [{:title "Item title 1"}
                                {:title "Item title 2"}
                                {:title "Item title 3"}] nil))])

   (component-info cart-list)
   (example "cart-list empty"
            [cart-list [] nil])
   (example "cart-list with two items of different workflow"
            [cart-list [{:title "Item title" :wfid 1}
                        {:title "Another title" :wfid 2}] nil])
   (example "cart-list with three items of same workflow and two of different"
            [cart-list [{:title "First title" :wfid 2} {:title "Second title" :wfid 1} {:title "Third title" :wfid 1} {:title "Fourth title" :wfid 1} {:title "Fifth title" :wfid 3}] nil])
   (example "cart-list with five items of same workflow but of two different forms"
            [cart-list [{:title "First form" :wfid 1 :formid 1} {:title "Second form" :wfid 1 :formid 2} {:title "First form" :wfid 1 :formid 1} {:title "Second form" :wfid 1 :formid 2} {:title "First form" :wfid 1 :formid 1}] nil])])
