(ns rems.test.table
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.table :refer [matches-filter matches-filters apply-filtering]]))

(deftest matches-filter-test
  (let [column-definitions {:fname-col {:sort-value :first-name}
                            :lname-col {:sort-value :last-name}}]
    (testing "mismatch"
      (is (= false (matches-filter column-definitions :fname-col "foo" {:first-name "bar"}))))
    (testing "exact match"
      (is (= true (matches-filter column-definitions :fname-col "foo" {:first-name "foo"}))))
    (testing "substring match"
      (is (= true (matches-filter column-definitions :fname-col "ba" {:first-name "foobar"}))))
    (testing "case insensitive match"
      (is (= true (matches-filter column-definitions :fname-col "Abc" {:first-name "abC"}))))))

(deftest matches-filters-test
  (let [column-definitions {:fname-col {:sort-value :first-name}
                            :lname-col {:sort-value :last-name}}
        item {:first-name "Aku" :last-name "Ankka"}]
    (testing "no filters"
      (is (= true (matches-filters column-definitions {} item))))
    (testing "one filter, matches"
      (is (= true (matches-filters column-definitions {:fname-col "Aku"} item))))
    (testing "one filter, no match"
      (is (= false (matches-filters column-definitions {:fname-col "x"} item))))
    (testing "many filters, all match"
      (is (= true (matches-filters column-definitions {:fname-col "Aku" :lname-col "Ankka"} item))))
    (testing "many filters, only some match"
      (is (= false (matches-filters column-definitions {:fname-col "Aku" :lname-col "x"} item))))))

(deftest apply-filtering-test
  (let [column-definitions {:fname-col {:sort-value :first-name}
                            :lname-col {:sort-value :last-name}}
        items [{:first-name "Aku" :last-name "Ankka"}
               {:first-name "Roope" :last-name "Ankka"}
               {:first-name "Hannu" :last-name "Hanhi"}]]
    (testing "empty list"
      (is (= [] (apply-filtering column-definitions {} []))))
    (testing "without filters"
      (is (= items
             (apply-filtering column-definitions {} items))))
    (testing "with filters"
      (is (= [{:first-name "Aku" :last-name "Ankka"}
              {:first-name "Roope" :last-name "Ankka"}]
             (apply-filtering column-definitions {:lname-col "Ankka"} items))))))
