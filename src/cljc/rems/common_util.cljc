(ns rems.common-util
  (:require [clojure.test :refer [deftest is testing]]))

(defn select-vals
  "Select values in map `m` specified by given keys `ks`.

  Values will be returned in the order specified by `ks`.

  You can specify a `default-value` that will be used if the
  key is not found in the map. This is like `get` function."
  [m ks & [default-value]]
  (vec (reduce #(conj %1 (get m %2 default-value)) [] ks)))

(defn index-by
  "Index the collection coll with given keys `ks`.

  Result is a map indexed by the first key
  that contains a map indexed by the second key."
  [ks coll]
  (if (empty? ks)
    (first coll)
    (->> coll
         (group-by (first ks))
         (map (fn [[k v]] [k (index-by (rest ks) v)]))
         (into {}))))

(defn distinct-by
  "Remove duplicates from sequence, comparing the value returned by key-fn.
   The first element that key-fn returns a given value for is retained.

   Order of sequence is not preserved in any way."
  [key-fn sequence]
  (map first (vals (group-by key-fn sequence))))

(defn andstr
  "Like `apply str coll` but only produces something if all the
  values are truthy like with `and`.

  Useful for statements like
  ```clj
  (str (andstr (:foo x) \"/\") (:bar y))
  ```
  See also `test-andstr` for examples."
  [& coll]
  (when (every? identity coll)
    (apply str coll)))

(deftest test-andstr
  (testing "when any argument is falsey the result is nil"
    (is (= nil (andstr nil 1 2 3)))
    (is (= nil (andstr 1 2 false 3))))
  (testing "when all arguments are truthy the results are concatenated"
    (let [x {:foo 2}]
      (is (= "2/" (andstr (:foo x) "/")))
      (is (= "(2)" (andstr "(" (:foo x) ")"))))))

(defn deep-merge [a b]
  (cond (and (sequential? a) (sequential? b))
        (let [max-length (max (count a) (count b))
              a (take max-length (concat a (repeat nil)))
              b (take max-length (concat b (repeat nil)))]
          (doall (map deep-merge a b)))

        (map? a)
        (merge-with deep-merge a b)

        :else b))

(deftest test-deep-merge
  (testing "merge nil"
    (is (= nil
           (deep-merge nil
                       nil)))
    (is (= {:a 1}
           (deep-merge nil
                       {:a 1})))
    (is (= {:a 1}
           (deep-merge {:a 1}
                       nil))))
  (testing "preserve false"
    (is (= {:b false}
           (deep-merge {:b :anything}
                       {:b false}))))
  (testing "merge maps"
    (is (= {:a 2}
           (deep-merge {:a 1}
                       {:a 2})))
    (is (= {:a 1 :b 2 :c 2}
           (deep-merge {:a 1 :b 1}
                       {:b 2 :c 2})))
    (is (= {:a {:b {:c 100 :d 2}}}
           (deep-merge {:a {:b {:c 1 :d 2}}}
                       {:a {:b {:c 100}}}))))
  (testing "merge vectors"
    (is (= [{:a 2}]
           (deep-merge [{:a 1}]
                       [{:a 2}])))
    (is (= [{:a 1 :b 2 :c 2}]
           (deep-merge [{:a 1 :b 1}]
                       [{:b 2 :c 2}])))
    (is (= [{:a 1} {:b 2}]
           (deep-merge [{:a 1}]
                       [nil {:b 2}])))
    (is (= [{:b 2} {:a 1}]
           (deep-merge [nil {:a 1}]
                       [{:b 2}]))))
  (testing "merge lists"
    (is (= [{:a 2}]
           (deep-merge '({:a 1})
                       '({:a 2}))))
    (is (= [{:a 1 :b 2 :c 2}]
           (deep-merge '({:a 1 :b 1})
                       '({:b 2 :c 2}))))))
