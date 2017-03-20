(ns rems.test.cart
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [ring.mock.request :refer :all]
            [rems.context :as context]
            [rems.cart :as cart]))

;; TODO: factor out if needed elsewhere
(use-fixtures
  :once
  (fn [f]
    (binding [context/*tempura* (fn [[k]] (str k))]
      (f))))

(defn check-row-text [row text]
  (is (= text (hiccup-text (first (hiccup-find [:td] row))))))

(deftest test-cart-list
  (let [c (cart/cart-list [{:title "D"} {:title "C"}])
        rows (hiccup-find [:tr] c)]
    (is (= 2 (count rows)))
    (check-row-text (first rows) "C")
    (check-row-text (second rows) "D")))

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
