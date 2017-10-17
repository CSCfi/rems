(ns rems.cart
  (:require [compojure.core :refer [POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.context :as context]
            [rems.db.catalogue :refer [get-catalogue-item-title
                                       get-localized-catalogue-item]]
            [rems.form :as form]
            [rems.guide :refer :all]
            [rems.text :refer :all]
            [rems.util :refer [select-values]]
            [ring.util.response :refer [redirect]]
            ))

(defn- button
  [cls action text value & [disabled?]]
  [:form.inline {:method "post" :action action}
   (anti-forgery-field)
   [:input {:type "hidden" :name "id" :value value}]
   [:button.btn {:type "submit"
                 :disabled disabled?
                 :class (str cls (if disabled? " disabled" ""))} text]])

(def ^:private button-primary
  (partial button "btn-primary"))

(def ^:private button-secondary
  (partial button "btn-secondary"))

(defn add-to-cart-button
  "Hiccup fragment that contains a button that adds the given item to the cart"
  [item]
  (let [disabled? (and (bound? #'context/*cart*) (contains? (set context/*cart*) (:id item)))]
    (button-primary "/cart/add" (text :t.cart/add) (:id item) disabled?)))

(defn remove-from-cart-button
  "Hiccup fragment that contains a button that removes the given item from the cart"
  [item]
  (button-secondary "/cart/remove" (text :t.cart/remove) (:id item)))

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

(defn- item-view [item & [apply-button?]]
  [:tr.cart-item {:class (if apply-button? "separator" "")}
   [:td.title (get-catalogue-item-title item)]
   [:td.commands
    (remove-from-cart-button item)
    (when apply-button? (apply-button [item]))]])

(defn- group-view [items]
  (if (= 1 (count items))
    (list (item-view (first items) true))
    (concat (map item-view items)
            [[:tr.separator [:td.commands.text-right {:colspan 2} (text-format :t.cart/apply-for-bundle (count items)) [:span.mr-3] (apply-button items)]]])))

(defn cart-list [items]
  (when-not (empty? items)
    [:div.outer-cart
     [:div.inner-cart
      [:div.cart-title
       [:i.fa.fa-shopping-cart]
       [:span (text-format :t.cart/header (count items))]]
      [:table.rems-table.cart
       (apply concat
              (let [key-fn #(select-values % [:wfid :formid])]
                (for [group (sort-by (comp key-fn first)
                                     (vals (group-by key-fn items)))]
                  (group-view (sort-by get-catalogue-item-title group)))))]]]))

(defn guide []
  (list
   (example "item-view, single"
            [:table.rems-table.cart
             (item-view {:title "Item title"} true)])
   (example "item-view, one of many has no apply button"
            [:table.rems-table.cart
             (item-view {:title "Item title"})])
   (example "group-view"
            [:table.rems-table.cart
             (group-view [{:title "Item title"}])])
   (example "cart-list empty"
            (cart-list []))
   (example "cart-list with two items of different workflow"
            (cart-list [{:title "Item title" :wfid 1} {:title "Another title" :wfid 2}]))
   (example "cart-list with three items of same workflow and two of different"
            (cart-list [{:title "First title" :wfid 2} {:title "Second title" :wfid 1} {:title "Third title" :wfid 1} {:title "Fourth title" :wfid 1} {:title "Fifth title" :wfid 3}]))
   (example "cart-list with five items of same workflow but of two different forms"
            (cart-list [{:title "First form" :wfid 1 :formid 1} {:title "Second form" :wfid 1 :formid 2} {:title "First form" :wfid 1 :formid 1} {:title "Second form" :wfid 1 :formid 2} {:title "First form" :wfid 1 :formid 1}]))
   ))
