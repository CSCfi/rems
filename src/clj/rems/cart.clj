(ns rems.cart
  (:require [rems.context :as context]
            [rems.text :refer :all]
            [rems.db.core :as db]
            [compojure.core :refer [defroutes POST]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :refer [redirect]]))

(defn- button
  [action text item]
  [:form.inline {:method "post" :action action}
   (anti-forgery-field)
   [:input {:type "hidden" :name "id" :value (:id item)}]
   [:button.btn.btn-primary {:type "submit"} text]])

(defn add-to-cart-button
  "Hiccup fragment that contains a button that adds the given item to the cart"
  [item]
  (button "/cart/add" (text :t.cart/add) item))

(defn remove-from-cart-button
  "Hiccup fragment that contains a button that removes the given item from the cart"
  [item]
  (button "/cart/remove" (text :t.cart/remove) item))

(defn get-cart-from-session
  "Computes the value for context/*cart*: a sequence of integer ids."
  [request]
  (map #(Long/parseLong %) (get-in request [:session :cart])))

(defn get-cart-items
  "Fetch items currently in cart from database"
  []
  (doall (for [i context/*cart*]
           (db/get-catalogue-item {:id i}))))

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
