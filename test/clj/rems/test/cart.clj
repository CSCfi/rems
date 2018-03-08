(ns rems.test.cart
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures :once fake-tempura-fixture)

(defn check-row-text [row text]
  (is (= text (hiccup-text (first (hiccup-find [:td] row))))))

#_
(deftest test-cart-list
  (let [c (cart/cart-list [{:title "D" :wfid 1 :formid 2}
                           {:title "C" :wfid 1 :formid 1}])
        rows (hiccup-find [:tr] c)
        title (first (hiccup-find [:div.cart-title] c))]
    (is (= 2 (count rows)))
    (testing "rows should be sorted"
      (check-row-text (first rows) "C")
      (check-row-text (second rows) "D"))
    (testing title
      (is title)
      (is (.contains (hiccup-text title) "2")))))

#_
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
