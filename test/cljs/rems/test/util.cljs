(ns rems.test.util
  (:require [cljs.test :refer-macros [deftest is testing]]
            [rems.util :refer [vec-dissoc]]))

(deftest vec-dissoc-test
  (is (vector? (vec-dissoc ["a"] 0)))
  (is (= [] (vec-dissoc ["a"] 0)))
  (is (= ["b", "c"] (vec-dissoc ["a", "b", "c"] 0)))
  (is (= ["a", "c"] (vec-dissoc ["a", "b", "c"] 1)))
  (is (= ["a", "b"] (vec-dissoc ["a", "b", "c"] 2))))
