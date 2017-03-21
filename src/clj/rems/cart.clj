(ns rems.cart
  (:require [rems.context :as context]
            [rems.example :refer :all]
            [rems.text :refer :all]
            [rems.form :as form]
            [compojure.core :refer [defroutes POST]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :refer [redirect]]
            [rems.db.catalogue :refer [get-localized-catalogue-item
                                       get-catalogue-item-title]]))

(defn- button
  [class action text value & [disabled?]]
  [:form.inline {:method "post" :action action}
   (anti-forgery-field)
   [:input {:type "hidden" :name "id" :value value}]
   [:button.btn {:type "submit"
                 :disabled disabled?
                 :class (str class (if disabled? " disabled" ""))} text]])

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
  "Computes the value for context/*cart*: a sequence of integer ids."
  [request]
  (map #(Long/parseLong %) (get-in request [:session :cart])))

(defn get-cart-items
  "Fetch items currently in cart from database"
  []
  (doall (for [i context/*cart*]
           (get-localized-catalogue-item {:id i}))))

(defn- handler [method {session :session {id :id} :params :as req}]
  (let [modifier (case method
                   :add conj
                   :remove disj)]
    (assoc (redirect "/catalogue" :see-other)
           :session (update session :cart #(set (modifier % id))))))

(defroutes cart-routes
  (POST "/cart/add" session
        (handler :add session))
  (POST "/cart/remove" session
        (handler :remove session)))

(defn- apply-button [item]
  [:a.btn.btn-primary {:href (form/link-to-item item)} (text :t.cart/apply)])

(defn- cart-item [item]
  [:tr
   [:td {:data-th ""} (get-catalogue-item-title item)]
   [:td.actions {:data-th ""}
    (apply-button item)
    (remove-from-cart-button item)]])

(defn cart-list [items]
  (when-not (empty? items)
    [:div.outer-cart
     [:div.inner-cart
      [:div.cart-title
       [:i.fa.fa-shopping-cart]
       [:span (text :t.cart/header)]]
      [:table.rems-table.cart
       (for [item (sort-by get-catalogue-item-title items)]
         (cart-item item))]]]))

(defn guide []
  (list
   (example "cart-item"
            [:table.rems-table.cart
             (cart-item {:title "Item title"})])
   (example "cart-list empty"
            (cart-list []))
   (example "cart-list with two items"
            (cart-list [{:title "Item title"} {:title "Another title"}]))))
