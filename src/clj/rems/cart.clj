(ns rems.cart
  (:require [compojure.core :refer [POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.context :as context]
            [rems.db.catalogue :refer [get-catalogue-item-title
                                       get-localized-catalogue-item]]
            [rems.form :as form]
            [rems.guide :refer :all]
            [rems.text :refer :all]
            [ring.util.response :refer [redirect]]
            [clojure.string :as str]))

(defn- button-primary
  [action text value & [disabled?]]
  [:form.inline {:method "post" :action action}
   (anti-forgery-field)
   [:input {:type "hidden" :name "id" :value value}]
   [:button.btn {:type "submit"
                 :disabled disabled?
                 :class (str "btn-primary" (if disabled? " disabled" ""))} text]])

(defn- button-close
  [action text value & [disabled?]]
  [:form.inline {:method "post" :action action}
   (anti-forgery-field)
   [:input {:type "hidden" :name "id" :value value}]
   [:button.btn {:type "submit"
                 :disabled disabled?
                 :aria-label text
                 :class (str "close" (if disabled? " disabled" ""))} "&times;"]])

(defn add-to-cart-button
  "Hiccup fragment that contains a button that adds the given item to the cart"
  [item]
  (let [disabled? (and (bound? #'context/*cart*) (contains? (set context/*cart*) (:id item)))]
    (button-primary "/cart/add" (text :t.cart/add) (:id item) disabled?)))

(defn remove-from-cart-button
  "Hiccup fragment that contains a button that removes the given item from the cart"
  [item]
  (button-close "/cart/remove" (text :t.cart/remove) (:id item)))

(defn get-cart-from-session
  "Computes the value for context/*cart*: a set of integer ids."
  [request]
  (get-in request [:session :cart]))

(defn get-cart-items
  "Fetch items currently in cart from database"
  []
  (doall (for [i context/*cart*]
           (get-localized-catalogue-item {:id i}))))

(defn- handler [modifier {session :session {id-string :id} :params :as req}]
  (let [id (Long/parseLong id-string)]
    (assoc (redirect "/catalogue" :see-other)
           :session (update session :cart #(set (modifier % id))))))

(defroutes cart-routes
  (POST "/cart/add" session (handler conj session))
  (POST "/cart/remove" session (handler disj session)))

(defn- apply-button [items]
  [:a.btn.btn-primary {:href (form/link-to-application items)} (text :t.cart/apply)])

(defn- item-view [item]
  [:span.cart-item
   [:span.title (get-catalogue-item-title item)]
   [:span (remove-from-cart-button item)]])

(defn- group-view [items]
  [:tr
   [:td {:data-th ""} (map item-view items)]
   [:td.commands {:data-th ""}
    (apply-button items)]])

(defn cart-list [items]
  (when-not (empty? items)
    [:div.outer-cart
     [:div.inner-cart
      [:div.cart-title
       [:i.fa.fa-shopping-cart]
       [:span (text-format :t.cart/header (count items))]]
      [:table.rems-table.cart
       (for [group (vals (group-by :wfid items))]
         (group-view (sort-by get-catalogue-item-title group)))]]]))

(defn guide []
  (list
   (example "item-view"
            [:table.rems-table.cart
             (item-view {:title "Item title"})])
   (example "group-view"
            [:table.rems-table.cart
             (group-view [{:title "Item title"}])])
   (example "cart-list empty"
            (cart-list []))
   (example "cart-list with two items of same workflow"
            (cart-list [{:title "Item title" :wfid 1} {:title "Another title" :wfid 1}]))
   (example "cart-list with two items of different workflow"
            (cart-list [{:title "Item title" :wfid 1} {:title "Another title" :wfid 2}]))))
