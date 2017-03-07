(ns rems.cart
  (:require [rems.context :as context]
            [rems.db.core :as db]
            [compojure.core :refer [defroutes POST]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.util.response :refer [redirect]]))

;; TODO: cart should store ids, not names. cart contents should be
;; fetched from db using the ids

(defn add-to-cart-button
  "Hiccup fragment that contains a button that adds the given item to the cart"
  [item]
  [:form.inline {:method "post" :action "/cart/add"}
   (anti-forgery-field)
   [:input {:type "hidden" :name "id" :value (:id item)}]
   [:button.btn-primary {:type "submit"} (context/*tempura* [:cart/add])]])

(defn get-cart-from-session
  "Computes the value for context/*cart*: a sequence of integer ids."
  [request]
  (map #(Long/parseLong %) (get-in request [:session :cart])))

(defn get-cart-items
  "Fetch items currently in cart from database"
  []
  (doall (for [i context/*cart*]
           (db/get-catalogue-item {:id i}))))

(defn- add-to-cart [session item-id]
  (update session :cart
          #(set (conj % item-id))))

(defroutes cart-routes
  (POST "/cart/add" {session :session params :params}
        (assoc (redirect "/catalogue" :see-other)
               :session (add-to-cart session (get params :id)))))
