(ns rems.test.contents
  (:require [clojure.test :refer :all]
            [rems.contents :refer :all]))

;; TODO some utilites for testing hiccup output

(defn check-row-text [row text]
  (is (= :tr (first row)))
  (let [cell (second row)]
    (is (= :td (first cell)))
    (is (= text (last cell)))))

(deftest test-catalogue-list
  (let [c (catalogue-list [{:title "B"} {:title "A"} {:title "C"}])
        elements (get c 2)]
    (is (= 3 (count elements)))
    (check-row-text (nth elements 0) "A")
    (check-row-text (nth elements 1) "B")
    (check-row-text (nth elements 2) "C")))

(deftest test-cart-list
  (let [c (cart-list ["D" "C"])
        elements (get c 2)]
    (is (= 2 (count elements)))
    (check-row-text (first elements) "C")
    (check-row-text (second elements) "D")))
