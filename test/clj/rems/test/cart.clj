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
                         :params {:id (str id)})
                  cart/cart-routes
                  :session))]
    (testing "empty session"
      (is (= #{12} (:cart (run nil "/cart/add" 12)))))
    (testing "add two"
      (is (= #{12 34} (-> nil
                          (run "/cart/add" 12)
                          (run "/cart/add" 34)
                          :cart))))
    (testing "add twice"
      (is (= #{12} (-> nil
                       (run "/cart/add" 12)
                       (run "/cart/add" 12)
                       :cart))))
    (testing "remove"
      (is (= #{56} (-> nil
                       (run "/cart/add" 12)
                       (run "/cart/add" 56)
                       (run "/cart/remove" 12)
                       :cart))))
    (testing "remove twice"
      (is (= #{56} (-> nil
                       (run "/cart/add" 12)
                       (run "/cart/add" 56)
                       (run "/cart/remove" 12)
                       (run "/cart/remove" 12)
                       :cart))))))
