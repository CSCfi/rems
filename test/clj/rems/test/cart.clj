(ns rems.test.cart
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [rems.cart :as cart]))

(deftest test-add-to-cart
  (let [run (fn [session path id]
              (-> (request :post path)
                  (assoc :session session
                         :params {:id id})
                  cart/cart-routes
                  :session))
        cart (comp sort :cart)]
    (testing "empty session"
      (is (= ["A"] (cart (run nil "/cart/add" "A")))))
    (testing "add two"
      (is (= ["A" "B"] (-> nil
                           (run "/cart/add" "A")
                           (run "/cart/add" "B")
                           cart))))
    (testing "add twice"
      (is (= ["A"] (-> nil
                       (run "/cart/add" "A")
                       (run "/cart/add" "A")
                       cart))))
    (testing "remove"
      (is (= ["C"] (-> nil
                       (run "/cart/add" "A")
                       (run "/cart/add" "C")
                       (run "/cart/remove" "A")
                       cart))))
    (testing "remove twice"
      (is (= ["C"] (-> nil
                       (run "/cart/add" "A")
                       (run "/cart/add" "C")
                       (run "/cart/remove" "A")
                       (run "/cart/remove" "A")
                       cart))))))
