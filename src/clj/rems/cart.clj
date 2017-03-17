(ns rems.cart
  (:require [rems.context :as context]
            [rems.text :refer :all]
            [rems.db.catalogue :as catalogue.db]
            [rems.form :as form]
            [compojure.core :refer [defroutes POST]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :refer [redirect]]))

(defn- button
  [class action text value & [disabled?]]
  [:form.inline {:method "post" :action action}
   (anti-forgery-field)
   [:input {:type "hidden" :name "id" :value value}]
   [:button.btn {:type "submit"
                 :disabled disabled?
                 :class (str class (if disabled? " disabled" ""))} text]])

(def button-primary
  (partial button "btn-primary"))

(def button-secondary
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

(defn checkout-cart-button
  "Hiccup fragment for a button that sends the applications for the cart."
  []
  (button-primary "/cart/checkout" (text :t.cart/checkout) :checkout))

(defn get-cart-from-session
  "Computes the value for context/*cart*: a sequence of integer ids."
  [request]
  (map #(Long/parseLong %) (get-in request [:session :cart])))

(defn get-cart-items
  "Fetch items currently in cart from database"
  []
  (doall (for [i context/*cart*]
           (catalogue.db/get-localized-catalogue-item {:id i}))))

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

(defn apply-button [item]
  [:a.btn.btn-primary {:href (form/link-to-item item)} (text :t.cart/apply)])
