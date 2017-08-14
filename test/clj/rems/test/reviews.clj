(ns rems.test.reviews
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.reviews :as reviews]
            rems.test.tempura))

(use-fixtures :once rems.test.tempura/fake-tempura-fixture)

(defn check-row-text [row text]
  (is (= text (hiccup-text (first (hiccup-find [:td] row))))))

(deftest test-revies
  (let [c (#'rems.reviews/reviews [{:id 2 :catalogue-item {:title "A"} :applicantuserid "tester"}
                                   {:id 3 :catalogue-item {:title "B"} :applicantuserid "tester"}
                                   {:id 1 :catalogue-item {:title "C"} :applicantuserid "tester"}])
        rows (hiccup-find [:tr.review] c)]
    (is (= 3 (count rows)))
    (check-row-text (nth rows 0) "1")
    (check-row-text (nth rows 1) "2")
    (check-row-text (nth rows 2) "3")))
