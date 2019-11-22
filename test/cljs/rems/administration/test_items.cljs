(ns rems.administration.test-items
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.administration.items :as items]))

(deftest add-item-test
  (is (vector? (items/add nil {})))
  (is (vector? (items/add [] {})))
  (is (= [{}] (items/add nil {})))
  (is (= [{}] (items/add [] {})))
  (is (= [{:foo "A"} {:foo "new"}] (items/add [{:foo "A"}] {:foo "new"})))
  (is (= [{:foo "A"} {:foo "B"} {:foo "new"}] (items/add [{:foo "A"} {:foo "B"}] {:foo "new"}))))

(deftest insert-item-test
  (is (vector? (items/insert nil 0 {})))
  (is (vector? (items/insert [] 0 {})))
  (is (= [{}] (items/insert nil 0 {})))
  (is (= [{}] (items/insert [] 0 {})))
  (is (= [{:foo "new"} {:foo "A"} {:foo "B"}] (items/insert [{:foo "A"} {:foo "B"}] 0 {:foo "new"})))
  (is (= [{:foo "A"} {:foo "new"} {:foo "B"}] (items/insert [{:foo "A"} {:foo "B"}] 1 {:foo "new"})))
  (is (= [{:foo "A"} {:foo "B"} {:foo "new"}] (items/insert [{:foo "A"} {:foo "B"}] 2 {:foo "new"}))))

(deftest remove-item-test
  (is (vector? (items/remove [{}] 0)))
  (is (= [] (items/remove [{}] 0)))
  (is (= [{:foo "B"} {:foo "C"}]
         (items/remove [{:foo "A"} {:foo "B"} {:foo "C"}] 0)))
  (is (= [{:foo "A"} {:foo "C"}]
         (items/remove [{:foo "A"} {:foo "B"} {:foo "C"}] 1)))
  (is (= [{:foo "A"} {:foo "B"}]
         (items/remove [{:foo "A"} {:foo "B"} {:foo "C"}] 2))))

(deftest move-item-up-test
  (is (vector? (items/move-up [{:foo "A"} {:foo "B"} {:foo "C"}] 1)))
  (is (= [{:foo "A"} {:foo "B"} {:foo "C"}]
         (items/move-up [{:foo "A"} {:foo "B"} {:foo "C"}] 0)))
  (is (= [{:foo "B"} {:foo "A"} {:foo "C"}]
         (items/move-up [{:foo "A"} {:foo "B"} {:foo "C"}] 1)))
  (is (= [{:foo "A"} {:foo "C"} {:foo "B"}]
         (items/move-up [{:foo "A"} {:foo "B"} {:foo "C"}] 2))))

(deftest move-item-down-test
  (is (vector? (items/move-down [{:foo "A"} {:foo "B"} {:foo "C"}] 1)))
  (is (= [{:foo "B"} {:foo "A"} {:foo "C"}]
         (items/move-down [{:foo "A"} {:foo "B"} {:foo "C"}] 0)))
  (is (= [{:foo "A"} {:foo "C"} {:foo "B"}]
         (items/move-down [{:foo "A"} {:foo "B"} {:foo "C"}] 1)))
  (is (= [{:foo "A"} {:foo "B"} {:foo "C"}]
         (items/move-down [{:foo "A"} {:foo "B"} {:foo "C"}] 2))))
