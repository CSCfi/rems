(ns rems.test.contents
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.contents :refer :all]))

(defn check-row-text [row text]
  (is (= text (hiccup-text (first (hiccup-find [:td] row))))))

(deftest test-catalogue-list
  (let [c (catalogue-list [{:title "B"} {:title "A"} {:title "C"}])
        rows (rest (hiccup-find [:tr] c))]
    (is (= 3 (count rows)))
    (check-row-text (nth rows 0) "A")
    (check-row-text (nth rows 1) "B")
    (check-row-text (nth rows 2) "C")))

(deftest test-cart-list
  (let [c (cart-list [{:title "D"} {:title "C"}])
        rows (rest (hiccup-find [:tr] c))]
    (is (= 2 (count rows)))
    (check-row-text (first rows) "C")
    (check-row-text (second rows) "D")))
