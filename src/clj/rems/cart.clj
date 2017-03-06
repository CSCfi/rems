(ns rems.cart
  (:require [rems.layout :as layout]
            [rems.context :as context]
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
   [:input {:type "hidden" :name "item" :value item}]
   [:button.btn-primary {:type "submit"} "Add to cart"]])

(defn- add-to-cart [session item-id]
  (update session :cart
          #(vec (conj % item-id))))

(defroutes cart-routes
  (POST "/cart/add" {session :session params :params}
        (assoc (redirect "/catalogue" :see-other)
               :session (add-to-cart session (get params :item)))))
