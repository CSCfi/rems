(ns rems.common.test-util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.common.util :refer [select-vals]]))

(deftest select-vals-test
  (is (= [] (select-vals nil nil)))
  (is (= [] (select-vals {:a 1 :b 2} nil)))
  (is (= [nil nil] (select-vals nil [:a :b])))
  (is (= [:happy :path] (select-vals {:a :happy :b :path} [:a :b])))
  (testing "with default-value"
    (is (= [1 3 2 :nope 3]
           (select-vals {:e 3 :b 2 :c 3 :a 1} [:a :e :b :d :e] :nope)))))

