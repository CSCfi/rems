(ns rems.test.cart
  (:require [clojure.test :refer :all]
            [rems.context :as context]
            [rems.cart :as cart]))

(deftest test-add-to-cart
  (let [add-to-cart #'cart/add-to-cart]
    (testing "empty session"
      (= ["A"] (sort (:cart (add-to-cart {} "A")))))
    (testing "add two"
      (= ["A" "B"] (sort (:cart (-> {}
                                    (add-to-cart "A")
                                    (add-to-cart "B"))))))
    (testing "add twice"
      (= ["A"] (sort (:cart (-> {}
                                (add-to-cart "A")
                                (add-to-cart "A"))))))))
